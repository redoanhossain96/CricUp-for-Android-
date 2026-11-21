/**
 * CricUp - Secure Firebase Cloud Functions
 * Provides backend-authenticated OTP generation, delivery, and verification
 * using the Firebase Admin SDK.
 */

const { onRequest, onCall, HttpsError } = require("firebase-functions/v2/https");
const admin = require("firebase-admin");
const crypto = require("crypto");

admin.initializeApp();
const db = admin.firestore();
const auth = admin.auth();

const OTP_COLLECTION = "otp_verifications";
const OTP_EXPIRY_MS = 5 * 60 * 1000; // 5 minutes
const RESEND_COOLDOWN_MS = 60 * 1000; // 60 seconds
const MAX_ATTEMPTS = 5;

// Helper: SHA-256 hash
function hashEmail(email) {
  return crypto.createHash("sha256").update(email.trim().toLowerCase()).digest("hex");
}

function hashCodeWithSalt(code, salt) {
  return crypto.createHash("sha256").update(`${salt}:${code}`).digest("hex");
}

/**
 * Cloud Function: sendEmailOtp
 * Generates secure 6-digit OTP, records salted hash in Firestore, and sends via email API.
 */
exports.sendEmailOtp = onCall({ cors: true }, async (request) => {
  const email = (request.data && request.data.email ? request.data.email : "").trim().toLowerCase();
  
  if (!email || !email.includes("@") || !email.includes(".")) {
    throw new HttpsError("invalid-argument", "সঠিক ইমেইল ঠিকানা প্রদান করুন (Invalid email address)");
  }

  const docId = hashEmail(email);
  const now = Date.now();
  const docRef = db.collection(OTP_COLLECTION).document(docId);

  // 1. Rate Limiting Check
  const existingSnap = await docRef.get();
  if (existingSnap.exists) {
    const data = existingSnap.data();
    const lastSentAt = data.createdAt || 0;
    const timeSinceLast = now - lastSentAt;
    if (timeSinceLast < RESEND_COOLDOWN_MS) {
      const waitSeconds = Math.ceil((RESEND_COOLDOWN_MS - timeSinceLast) / 1000);
      throw new HttpsError("resource-exhausted", `অনুগ্রহ করে ${waitSeconds} সেকেন্ড অপেক্ষা করুন (Cooldown active)`);
    }
  }

  // 2. Generate cryptographically secure 6-digit OTP
  const code = crypto.randomInt(100000, 1000000).toString();
  const salt = crypto.randomUUID();
  const codeHash = hashCodeWithSalt(code, salt);
  const expiresAt = now + OTP_EXPIRY_MS;

  // 3. Write to Firestore via Admin SDK (bypasses security rules)
  await docRef.set({
    emailHash: docId,
    codeHash: codeHash,
    salt: salt,
    createdAt: now,
    expiresAt: expiresAt,
    attempts: 0,
    isUsed: false
  });

  // 4. Dispatch Email via configured provider (Resend, SendGrid, Brevo)
  let dispatched = false;
  const resendKey = process.env.RESEND_API_KEY;
  const sendgridKey = process.env.SENDGRID_API_KEY;
  const brevoKey = process.env.BREVO_API_KEY;

  if (resendKey) {
    try {
      const res = await fetch("https://api.resend.com/emails", {
        method: "POST",
        headers: {
          "Authorization": `Bearer ${resendKey}`,
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          from: "CricUp Security <onboarding@resend.dev>",
          to: [email],
          subject: `CricUp Verification Code: ${code}`,
          html: `
            <div style="font-family: Arial, sans-serif; background-color: #061224; color: #ffffff; padding: 24px; border-radius: 12px; max-width: 500px;">
              <h2 style="color: #00E676; margin-top: 0;">🏏 CricUp Cricket Management</h2>
              <p style="font-size: 15px; color: #cfd8dc;">আপনার CricUp একাউন্ট ভেরিফিকেশন কোড:</p>
              <div style="background-color: #0b1e38; border: 2px solid #FFD700; border-radius: 8px; padding: 16px; text-align: center; margin: 20px 0;">
                <span style="font-size: 32px; font-weight: bold; letter-spacing: 6px; color: #FFD700;">${code}</span>
              </div>
              <p style="font-size: 12px; color: #90a4ae;">এই কোডটি আগামী ৫ মিনিট কার্যকর থাকবে। কাউকে এই কোড শেয়ার করবেন না।</p>
            </div>
          `
        })
      });
      dispatched = res.ok;
    } catch (err) {
      console.error("Resend delivery failed:", err);
    }
  } else if (brevoKey) {
    try {
      const res = await fetch("https://api.brevo.com/v3/smtp/email", {
        method: "POST",
        headers: {
          "api-key": brevoKey,
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          sender: { name: "CricUp Cricket", email: "auth@cricup.app" },
          to: [{ email: email }],
          subject: `CricUp Verification Code: ${code}`,
          htmlContent: `
            <div style="font-family: Arial, sans-serif; background-color: #061224; color: #ffffff; padding: 24px; border-radius: 12px; max-width: 500px;">
              <h2 style="color: #00E676; margin-top: 0;">🏏 CricUp Cricket Management</h2>
              <p style="font-size: 15px; color: #cfd8dc;">আপনার CricUp একাউন্ট ভেরিফিকেশন কোড:</p>
              <div style="background-color: #0b1e38; border: 2px solid #FFD700; border-radius: 8px; padding: 16px; text-align: center; margin: 20px 0;">
                <span style="font-size: 32px; font-weight: bold; letter-spacing: 6px; color: #FFD700;">${code}</span>
              </div>
              <p style="font-size: 12px; color: #90a4ae;">এই কোডটি আগামী ৫ মিনিট কার্যকর থাকবে। কাউকে এই কোড শেয়ার করবেন না।</p>
            </div>
          `
        })
      });
      dispatched = res.ok;
    } catch (err) {
      console.error("Brevo delivery failed:", err);
    }
  }

  return {
    success: true,
    expiresAt: expiresAt,
    email: email,
    dispatched: dispatched
  };
});

/**
 * Cloud Function: verifyEmailOtp
 * Verifies code against salted hash, marks OTP consumed, and mints Firebase Custom Auth Token.
 */
exports.verifyEmailOtp = onCall({ cors: true }, async (request) => {
  const email = (request.data && request.data.email ? request.data.email : "").trim().toLowerCase();
  const code = (request.data && request.data.code ? request.data.code : "").trim();

  if (!email || !code || code.length !== 6) {
    throw new HttpsError("invalid-argument", "ইমেইল এবং ৬ সংখ্যার কোড আবশ্যক (Email and 6-digit code required)");
  }

  const docId = hashEmail(email);
  const docRef = db.collection(OTP_COLLECTION).document(docId);
  const snap = await docRef.get();

  if (!snap.exists) {
    throw new HttpsError("not-found", "কোনো সক্রিয় ভেরিফিকেশন কোড পাওয়া যায়নি (No active OTP found)");
  }

  const data = snap.data();
  const now = Date.now();

  if (data.isUsed) {
    throw new HttpsError("failed-precondition", "এই কোডটি ইতিমধ্যে ব্যবহার করা হয়েছে (Code already used)");
  }

  if (now > (data.expiresAt || 0)) {
    throw new HttpsError("deadline-exceeded", "ভেরিফিকেশন কোডের মেয়াদ শেষ হয়ে গেছে (Code expired)");
  }

  const currentAttempts = data.attempts || 0;
  if (currentAttempts >= MAX_ATTEMPTS) {
    throw new HttpsError("permission-denied", "অতিরিক্ত ভুল চেষ্টার কারণে কোডটি লক করা হয়েছে (Too many attempts)");
  }

  const expectedHash = data.codeHash;
  const salt = data.salt;
  const enteredHash = hashCodeWithSalt(code, salt);

  if (enteredHash !== expectedHash) {
    const newAttempts = currentAttempts + 1;
    await docRef.update({ attempts: newAttempts });
    const remaining = MAX_ATTEMPTS - newAttempts;
    throw new HttpsError("unauthenticated", `ভুল ভেরিফিকেশন কোড। অবশিষ্ট চেষ্টা: ${remaining} বার।`);
  }

  // Successful verification: mark as consumed
  await docRef.update({
    isUsed: true,
    verifiedAt: now
  });

  // Ensure Firebase Auth User exists and issue Custom Token
  let userRecord;
  try {
    userRecord = await auth.getUserByEmail(email);
  } catch (err) {
    if (err.code === "auth/user-not-found") {
      userRecord = await auth.createUser({
        email: email,
        emailVerified: true,
        displayName: email.split("@")[0]
      });
    } else {
      throw new HttpsError("internal", `User lookup failed: ${err.message}`);
    }
  }

  // Mint Firebase Custom Token for client login
  const customToken = await auth.createCustomToken(userRecord.uid, {
    provider: "email_otp"
  });

  return {
    success: true,
    customToken: customToken,
    uid: userRecord.uid,
    email: userRecord.email
  };
});
