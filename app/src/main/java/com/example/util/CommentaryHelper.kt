package com.example.util

import com.example.data.model.BallEvent
import com.example.data.model.DismissalType
import com.example.data.model.ExtraType

object CommentaryHelper {

    fun formatCommentary(ball: BallEvent, lang: String = "bn"): String {
        val isEn = lang.equals("en", ignoreCase = true)
        val bowler = ball.bowlerName.ifBlank { if (isEn) "Bowler" else "বোলার" }
        val striker = ball.strikerName.ifBlank { if (isEn) "Batter" else "ব্যাটার" }
        val dismissed = (ball.dismissedPlayerName ?: striker).ifBlank { if (isEn) "Batter" else "ব্যাটার" }
        val fielder = ball.assistingPlayerName?.takeIf { it.isNotBlank() }
            ?: if (isEn) "the fielder" else "ফিল্ডার"

        return if (isEn) {
            formatEnglishCommentary(ball, bowler, striker, dismissed, fielder)
        } else {
            formatBengaliCommentary(ball, bowler, striker, dismissed, fielder)
        }
    }

    private fun pickVariant(ball: BallEvent, pool: List<String>, categorySalt: Int): String {
        if (pool.isEmpty()) return ""
        val progression = when {
            ball.ballSeq > 0 -> ball.ballSeq
            ball.overNumber > 0 || ball.ballInOver > 0 -> ball.overNumber * 6 + ball.ballInOver
            ball.id.isNotBlank() -> (ball.id.hashCode() and 0x7FFFFFFF)
            else -> ((ball.timestamp xor (ball.timestamp ushr 32)).toInt() and 0x7FFFFFFF)
        }
        val matchOffset = ((ball.matchId.hashCode() * 17 + categorySalt * 31) and 0x7FFFFFFF)
        val index = ((progression + matchOffset) and 0x7FFFFFFF) % pool.size
        return pool[index]
    }

    private fun formatBengaliCommentary(
        ball: BallEvent,
        bowler: String,
        striker: String,
        dismissed: String,
        fielder: String
    ): String {
        if (ball.dismissalType == DismissalType.RETIRED_HURT) {
            return pickVariant(
                ball,
                listOf(
                    "রিটায়ার্ড হার্ট! চোট বা অসুস্থতাজনিত কারণে সাময়িকভাবে মাঠ ছাড়ছেন $dismissed (আউট নয় — পরে আবার ব্যাট করতে পারবেন)।",
                    "খেলায় বিরতি! শারীরিক অস্বস্তির কারণে প্যাভিলিয়নে ফিরছেন $dismissed—নিয়ম অনুযায়ী এটি উইকেট নয়।",
                    "রিটায়ার্ড হার্ট হলেন $dismissed। দলের প্রয়োজনে সুস্থ হয়ে পরবর্তীতে আবার ক্রিজে ফেরার সুযোগ থাকছে।"
                ),
                categorySalt = 101
            )
        }

        if (ball.isWicket) {
            val runsNote = if (ball.totalRunsOnBall > 0) " (এই বলে ${ball.totalRunsOnBall} রান)" else ""
            val wicketText = when (ball.dismissalType) {
                DismissalType.BOWLED -> pickVariant(
                    ball,
                    listOf(
                        "আউট! ক্লিন বোল্ড! $bowler এর বিধ্বংসী ইনসুইংগারে তাসের ঘরের মতো ভেঙে গেল $dismissed এর স্টাম্প!",
                        "উড়ে গেল অফ-স্টাম্প! $bowler এর নিখুঁত ইয়র্কারের কোনো জবাবই ছিল না $dismissed এর কাছে!",
                        "ম্যাজিক ডেলিভারি! ব্যাটের ফাঁক গলে সরাসরি মিডল স্টাম্প উপড়ে ফেললেন $bowler—সাজঘরে $dismissed!",
                        "আউট! বড় শট খেলতে গিয়ে লাইন মিস করলেন $dismissed, আর সেই সুযোগে স্টাম্প ছত্রখান করলেন $bowler!",
                        "দুর্দান্ত পেস আর সিম মুভমেন্ট! বেলগুলো উড়ে অনেক দূরে পড়ল—$bowler এর হাতে বিদায় নিলেন $dismissed!",
                        "ভেতরে ঢোকা আগুনে ডেলিভারি! ব্যাটার বুঝে ওঠার আগেই স্টাম্পে আঘাত হানলেন $bowler!",
                        "গ্যালারিতে গর্জন! ক্রিজ জুড়ে জমে যাওয়ার আগেই $dismissed এর স্টাম্প ভেঙে ব্রেকথ্রু আনলেন $bowler!",
                        "বোল্ড আউট! অফ-স্টাম্পের বেল উড়িয়ে দিয়ে রাজসিক উদযাপনে মেতে উঠলেন $bowler!"
                    ),
                    categorySalt = 11
                )

                DismissalType.CAUGHT -> pickVariant(
                    ball,
                    listOf(
                        "আউট! ক্যাচ আউট! $striker এর টাইমিংয়ে গড়বড়, আকাশে ওঠা বল নিরাপদে তালুবন্দি করলেন $fielder ($bowler)!",
                        "অবিশ্বাস্য ক্যাচ! ঝাঁপিয়ে পড়ে শূন্যে ভেসে বল লুফে নিলেন $fielder—$bowler পেলেন দারুণ এক উইকেট!",
                        "ফাঁদে পা দিলেন $dismissed! $bowler এর বুদ্ধিদীপ্ত বলে উড়িয়ে মারতে গিয়ে সরাসরি $fielder এর হাতে ক্যাচ!",
                        "দারুণ ব্রেকথ্রু! বাউন্ডারি লাইনের কিনারায় দুর্দান্ত ভারসাম্য রেখে ক্যাচটি নিলেন $fielder!",
                        "স্লোয়ারের জালে আটকা! অসতর্ক শটে সহজ ক্যাচ উঠে গেল—কোনো ভুল করলেন না $fielder, বিদায় $dismissed!",
                        "চাপের মুখে ভুল শট! $bowler এর বলে ডিপে দাঁড়ানো $fielder এর বিশ্বস্ত হাতে জমা পড়ল বল—আউট!",
                        "অফ-স্টাম্পের বাইরের বল তাড়া করতে গিয়ে বিপদ! দারুণ ক্যাচে কাজ সারলেন $fielder, সফল $bowler!",
                        "ফিল্ডিংয়ের চমৎকার প্রদর্শনী! $bowler এর কৌশল শতভাগ সফল—$fielder এর ক্যাচে সাজঘরের পথ ধরলেন $dismissed!"
                    ),
                    categorySalt = 12
                )

                DismissalType.CAUGHT_BEHIND -> pickVariant(
                    ball,
                    listOf(
                        "আউট! কট বিহাইন্ড! $bowler এর ধারালো ডেলিভারিতে ব্যাটের আলতো ছোঁয়া, কিপার $fielder এর গ্লাভসে নিখুঁত ক্যাচ!",
                        "সূক্ষ্ম আউটসাইড এজ! শব্দ পেতেই উল্লাস—উইকেটের পেছনে ঝাঁপিয়ে অসাধারণ ক্যাচ নিলেন $fielder!",
                        "দুর্দান্ত সিমিং ডেলিভারি! অফ-স্টাম্পের বাইরের বলে খোঁচা দিয়ে $fielder এর হাতে ধরা পড়লেন $dismissed!",
                        "সজাগ দৃষ্টি আর ক্ষিপ্র রিফ্লেক্স! $bowler এর বলে $dismissed এর ব্যাটের কানা ছুঁয়ে আসা বল লুফে নিলেন $fielder!",
                        "কী দারুণ বোলিং! ব্যাটারকে খেলতে বাধ্য করলেন $bowler, আর পেছন থেকে সহজ ক্যাচ নিলেন $fielder!",
                        "আম্পায়ারের আঙুল উপরে! ব্যাটের হালকা ছোঁয়ায় $fielder এর হাতে ধরা পড়তেই বিদায় নিলেন $dismissed!"
                    ),
                    categorySalt = 13
                )

                DismissalType.LBW -> pickVariant(
                    ball,
                    listOf(
                        "আউট! এলবিডব্লিউ! $bowler এর তীব্র আবেদনের সাথে সাথেই আঙুল তুললেন আম্পায়ার—প্লাম্ব ইন ফ্রন্ট $dismissed!",
                        "সোজা স্টাম্পের সামনে প্যাডে আঘাত! $bowler এর নিখুঁত ডেলিভারিতে এলবিডব্লিউর শিকার হলেন $dismissed!",
                        "লাইন ধরে আসা বিপজ্জনক বল! সুইপ করতে গিয়ে মিস করলেন $dismissed—সোজা প্যাডে, নিশ্চিত আউট!",
                        "আউট! একটুও দেরি করেননি আম্পায়ার! $bowler এর জোরালো ইনসুইংয়ে স্টাম্পের সামনেই আটকা পড়লেন $dismissed!",
                        "গতির সাথে পরাস্ত! ব্যাট নামানোর আগেই বল আঘাত হানল প্যাডে—$bowler এর ঝুলিতে গুরুত্বপূর্ণ এলবিডব্লিউ!",
                        "নিখুঁত জায়গায় বল ফেলেছেন $bowler! সোজা মিডল স্টাম্প বরাবর—এলবিডব্লিউ হয়ে ফিরলেন $dismissed!"
                    ),
                    categorySalt = 14
                )

                DismissalType.RUN_OUT -> pickVariant(
                    ball,
                    listOf(
                        "আউট! রান আউট! $fielder এর বুলেট গতির সরাসরি থ্রো ভেঙে দিল স্টাম্প—সাজঘরে ফিরছেন $dismissed!",
                        "কী ভয়াবহ ভুল বোঝাবুঝি! দুই ব্যাটার মাঝপথে আটকে গেলেন, সুযোগ কাজে লাগিয়ে $dismissed কে রান আউট করলেন $fielder!",
                        "চিতার গতিতে ফিল্ডিং! ডাইভ দিয়েও ক্রিজ বাঁচাতে পারলেন না $dismissed—$fielder এর অবিশ্বাস্য রান আউট!",
                        "এক রান নিতে গিয়ে চড়া মূল্য দিতে হলো! $fielder এর নিখুঁত থ্রোতে বিদায় নিলেন $dismissed!",
                        "চমৎকার গ্রাউন্ড ফিল্ডিং ও ক্ষিপ্র থ্রো! অল্পের জন্য ক্রিজে পৌঁছাতে পারলেন না $dismissed—রান আউট!",
                        "চাপের মুহূর্তে স্নায়ু ধরে রাখলেন $fielder! স্টাম্প ভাঙতেই হতাশ হয়ে মাঠ ছাড়লেন $dismissed!"
                    ),
                    categorySalt = 15
                )

                DismissalType.STUMPED -> pickVariant(
                    ball,
                    listOf(
                        "আউট! স্টাম্পড! ক্রিজ ছেড়ে বেরোতেই বল ঘুরিয়ে দিলেন $bowler, আর চোখের পলকে স্টাম্প ভাঙলেন $fielder!",
                        "বিদ্যুৎ গতির গ্লাভসওয়ার্ক! $dismissed পা ফেরানোর আগেই বেল ফেলে দিলেন $fielder—দুর্দান্ত স্টাম্পিং!",
                        "$bowler এর চতুর ফ্লাইটে পরাস্ত ব্যাটার! ভারসাম্য হারিয়ে ক্রিজের বাইরে যেতেই স্টাম্পড হলেন $dismissed!",
                        "বড় শট মারতে চেয়েছিলেন $dismissed, কিন্তু বল ঘুরে বেরিয়ে যেতেই অসাধারণ স্টাম্পিং করলেন $fielder!",
                        "চমৎকার জুটি বোলার ও কিপারের! $bowler বিট করলেন আর $fielder স্টাম্প ভেঙে বিদায় করলেন $dismissed কে!"
                    ),
                    categorySalt = 16
                )

                DismissalType.HIT_WICKET -> pickVariant(
                    ball,
                    listOf(
                        "অবিশ্বাস্য! হিট উইকেট! $bowler এর বলে ব্যাকফুটে খেলতে গিয়ে নিজের স্টাম্প নিজেই ভেঙে ফেললেন $dismissed!",
                        "নিজের ভুলেই সর্বনাশ! শট খেলার ভারসাম্য হারিয়ে স্টাম্পে আঘাত হানলেন $dismissed—হিট উইকেট!",
                        "দুর্ভাগ্যজনক বিদায়! $bowler এর শর্ট বল সামলাতে গিয়ে ক্রিজের পেছনে স্টাম্প ভেঙে ফেললেন $dismissed!",
                        "হতাশায় মাথায় হাত! $bowler এর ডেলিভারিতে পেছনে সরে খেলতে গিয়ে হিট উইকেট হলেন $dismissed!"
                    ),
                    categorySalt = 17
                )

                DismissalType.MANKAD -> pickVariant(
                    ball,
                    listOf(
                        "আউট! মানকাডিং! বল ছাড়ার আগেই নন-স্ট্রাইকার ক্রিজ ছাড়ায় $dismissed কে রান আউট করলেন $bowler!",
                        "নাটকীয় মুহূর্ত! নন-স্ট্রাইকিং প্রান্তে ক্রিজের বাইরে থাকায় বেল তুলে নিয়ে $dismissed কে আউট করলেন $bowler!",
                        "বোলারের তীক্ষ্ণ নজর! ঝুঁকি নিয়ে ক্রিজ ছাড়ার মাশুল দিয়ে মানকাড আউট হলেন $dismissed!"
                    ),
                    categorySalt = 18
                )

                DismissalType.RETIRED_OUT -> pickVariant(
                    ball,
                    listOf(
                        "রিটায়ার্ড আউট! কৌশলগত কারণে একেবারেই মাঠ ছাড়লেন $dismissed (বোলার: $bowler)।",
                        "উইকেটের পতন—রিটায়ার্ড আউট হয়ে ইনিংস সমাপ্ত করে প্যাভিলিয়নে ফিরছেন $dismissed।",
                        "রিটায়ার্ড আউট! দলের সিদ্ধান্তে ক্রিজে জায়গা ছেড়ে দিয়ে বিদায় নিলেন $dismissed।"
                    ),
                    categorySalt = 19
                )

                DismissalType.TIMED_OUT -> listOf("আউট! টাইমড আউট! নির্ধারিত সময়ের মধ্যে ক্রিজে পৌঁছাতে না পারায় আউট হলেন $dismissed!").first()
                DismissalType.OBSTRUCTING_FIELD -> listOf("আউট! ফিল্ডিংয়ে বাধা দেওয়ায় নিয়ম অনুযায়ী আউট ঘোষিত হলেন $dismissed!").first()
                DismissalType.HIT_BALL_TWICE -> listOf("আউট! নিয়ম ভেঙে বলকে দুবার আঘাত করায় আউট হলেন $dismissed!").first()
                DismissalType.HANDLED_BALL -> listOf("আউট! হ্যান্ডল্ড দ্য বলের কারণে উইকেট হারালেন $dismissed!").first()

                else -> pickVariant(
                    ball,
                    listOf(
                        "আউট! $bowler এর আঘাতে সাজঘরে ফিরতে হলো $dismissed কে!",
                        "বড় উইকেট! $bowler এর চমৎকার ডেলিভারিতে বিদায় নিলেন $dismissed!"
                    ),
                    categorySalt = 20
                )
            }
            return wicketText + runsNote
        }

        if (ball.extraType == ExtraType.NO_BALL) {
            val total = ball.totalRunsOnBall
            return when {
                ball.runsOffBat == 6 -> pickVariant(
                    ball,
                    listOf(
                        "নো বল আর তাতেই বিশাল ছক্কা! $striker এর দ্বিগুণ শাস্তিতে এল $total রান—পরের বল ফ্রি হিট!",
                        "ওভারস্টেপের চরম মাশুল! নো বলে গগনচুম্বী ছক্কা হাঁকালেন $striker, সামনে আরও একটি ফ্রি হিট!",
                        "বোলারের জন্য দুঃস্বপ্ন! একদিকে নো বল, অন্যদিকে $striker এর ব্যাট থেকে ছয় রান—মোট $total রান!"
                    ),
                    categorySalt = 21
                )

                ball.runsOffBat == 4 -> pickVariant(
                    ball,
                    listOf(
                        "নো বলেই চমৎকার চার! $bowler এর ভুলের পুরো ফায়দা তুললেন $striker, পরের বল ফ্রি হিট!",
                        "সীমানার ওপারে চার এবং আম্পায়ারের নো বল সংকেত! ঝুলিতে এল $total রান, থাকছে ফ্রি হিট!",
                        "নিখুঁত শাস্তি! $bowler ওভারস্টেপ করতেই বাউন্ডারি আদায় করে নিলেন $striker ($total রান)!"
                    ),
                    categorySalt = 22
                )

                ball.runsOffBat > 0 -> pickVariant(
                    ball,
                    listOf(
                        "নো বল! দাগ পেরিয়ে ফেলেছেন $bowler—এই বলে এল মোট $total রান, পরের বল ফ্রি হিট!",
                        "ফ্রি হিট আসছে! নো বল থেকে $striker নিলেন ব্যাট-রান, সব মিলিয়ে স্কোরবোর্ডে যোগ হলো $total রান!",
                        "নিয়ন্ত্রণ হারালেন $bowler! নো বলের সাথে অতিরিক্ত দৌড়ে এল $total রান, রোমাঞ্চকর ফ্রি হিট অপেক্ষায়!"
                    ),
                    categorySalt = 23
                )

                else -> pickVariant(
                    ball,
                    listOf(
                        "নো বল! ক্রিজের দাগ অতিক্রম করেছেন $bowler—অতিরিক্ত ${ball.extraRuns} রান এবং পরের বল ফ্রি হিট!",
                        "উত্তেজনা তুঙ্গে! আম্পায়ারের হাত প্রসারিত—নো বল! দল পেল ${ball.extraRuns} রান, সামনে ফ্রি হিট!",
                        "ওভারস্টেপ করে বিপদ ডেকে আনলেন $bowler! অতিরিক্ত ${ball.extraRuns} রানের সাথে আসছে ফ্রি হিট!",
                        "লক্ষ্যভ্রষ্ট ডেলিভারি—নো বল! চাপ বাড়ছে $bowler এর ওপর, পরের বলেই হাত খোলার সুযোগ পাচ্ছেন $striker!",
                        "ফ্রি হিটের হাতছানি! নো বল দিয়ে ফেলায় ${ball.extraRuns} অতিরিক্ত রান উপহার দিলেন $bowler!"
                    ),
                    categorySalt = 24
                )
            }
        }

        if (ball.extraType == ExtraType.WIDE) {
            return if (ball.extraRuns >= 5) {
                pickVariant(
                    ball,
                    listOf(
                        "ওয়াইড ও বাউন্ডারি—একসাথে ${ball.extraRuns} রান! কিপারকে ফাঁকি দিয়ে বল সীমানার বাইরে!",
                        "হতাশাজনক বল $bowler এর! প্রচণ্ড ওয়াইডের পর বল চলে গেল বাউন্ডারিতে, বিনা পরিশ্রমেই ${ball.extraRuns} রান!",
                        "পুরোপুরি দিকহারা ডেলিভারি! স্টাম্পের অনেক বাইরে দিয়ে বল বাউন্ডারি পেরোতেই স্কোরবোর্ডে যোগ হলো ${ball.extraRuns} ওয়াইড রান!"
                    ),
                    categorySalt = 25
                )
            } else if (ball.extraRuns > 1) {
                pickVariant(
                    ball,
                    listOf(
                        "ওয়াইড বলের পাশাপাশি দৌড়ে আরও রান! $bowler এর এই বলে অতিরিক্ত এল ${ball.extraRuns} রান!",
                        "লাইন মিস করলেন $bowler, সুযোগ বুঝে অতিরিক্ত ${ball.extraRuns} রান তুলে নিলেন ব্যাটাররা!"
                    ),
                    categorySalt = 26
                )
            } else {
                pickVariant(
                    ball,
                    listOf(
                        "ওয়াইড! লেগ-স্টাম্পের অনেক বাইরে বল ছুড়লেন $bowler, অতিরিক্ত ${ball.extraRuns} রান।",
                        "দিকহারা ডেলিভারি! অফ-স্টাম্পের নাগালের বাইরে চলে যাওয়ায় আম্পায়ারের ওয়াইড সংকেত (+${ball.extraRuns} রান)।",
                        "চাপে পড়ে লাইন হারালেন $bowler—দুই হাত মেলে ওয়াইড ঘোষণা করলেন আম্পায়ার!",
                        "সহজ উপহার! অযথা ওয়াইড দিয়ে বোলিং সাইডের চাপ কিছুটা বাড়িয়ে দিলেন $bowler (+${ball.extraRuns} রান)।",
                        "স্লো ওয়াইড ইয়র্কারের চেষ্টায় লক্ষ্যভ্রষ্ট $bowler, অতিরিক্ত ${ball.extraRuns} রান যোগ হলো স্কোরবোর্ডে।",
                        "শর্ট ও ওয়াইড! ব্যাট ছোঁয়াতে না পারলেও স্কোরবোর্ডে ফ্রি ${ball.extraRuns} রান পেল ব্যাটিং দল।"
                    ),
                    categorySalt = 27
                )
            }
        }

        if (ball.extraType == ExtraType.BYE) {
            return pickVariant(
                ball,
                listOf(
                    "বাই রান! ব্যাট ও কিপার উভয়কেই পরাস্ত করে বল চলে গেল পেছনে—${ball.extraRuns} বাই রান!",
                    "চতুর রানিং! বল কিপারের গ্লাভস ফসকাতেই দ্রুত দৌড়ে ${ball.extraRuns} বাই রান নিলেন ব্যাটাররা।",
                    "বাড়তি বাউন্স সামলাতে পারলেন না কিপার, সুযোগ কাজে লাগিয়ে স্কোরবোর্ডে ${ball.extraRuns} বাই রান!",
                    "বল ব্যাটে না লাগলেও সজাগ চোখে ${ball.extraRuns} রান ছিনিয়ে নিলেন দুই ব্যাটার!",
                    "বাই থেকে মূল্যবান ${ball.extraRuns} রান—উইকেটের পেছনে মিসের সুযোগ ছাড়লেন না ব্যাটাররা।"
                ),
                categorySalt = 28
            )
        }

        if (ball.extraType == ExtraType.LEG_BYE) {
            return pickVariant(
                ball,
                listOf(
                    "লেগ বাই! প্যাডে লেগে বল চলে গেল ফাঁকা জায়গায়, অনায়াসে ${ball.extraRuns} রান নিলেন ব্যাটাররা।",
                    "থাই প্যাডে লেগে দিক বদলাল বল—আবেদন নাকচ হতেই ${ball.extraRuns} লেগ বাই রান যোগ হলো!",
                    "ফ্লিক করতে গিয়ে মিস করলেও প্যাডে লেগে মিলল ${ball.extraRuns} লেগ বাই রান।",
                    "$bowler এর ভেতরে ঢোকা বল প্যাডে লেগে গড়িয়ে গেল—ব্যাটারদের ঝুলিতে ${ball.extraRuns} লেগ বাই রান।",
                    "গতিময় ডেলিভারি প্যাডে আঘাত হেনে দূরে যেতেই চটপট ${ball.extraRuns} লেগ বাই রান তুলে নিলেন!"
                ),
                categorySalt = 29
            )
        }

        if (ball.extraType == ExtraType.PENALTY) {
            return pickVariant(
                ball,
                listOf(
                    "পেনাল্টি রান! নিয়ম ভঙ্গের কারণে ব্যাটিং দলের খাতায় যোগ হলো অতিরিক্ত ${ball.extraRuns} রান!",
                    "বড় সুবিধা! আম্পায়ারের পেনাল্টি সিদ্ধান্তে বিনা বলেই মিলল ${ball.extraRuns} রান।",
                    "ফিল্ডিং দলের ভুলের মাশুল—স্কোরবোর্ডে যোগ হলো ${ball.extraRuns} পেনাল্টি রান!"
                ),
                categorySalt = 30
            )
        }

        return when (ball.runsOffBat) {
            6 -> pickVariant(
                ball,
                listOf(
                    "বিশাল ছক্কা! $striker এর গগনচুম্বী হিটে বল সোজা গ্যালারিতে, দর্শকদের বাঁধভাঙা উল্লাস!",
                    "উড়িয়ে মারলেন $striker! $bowler এর বল লং-অনের মাথার ওপর দিয়ে সোজা মাঠের বাইরে—৬ রান!",
                    "দুর্দান্ত পুল শট! $striker এর ব্যাটে যেন আগুনের ফুলকি—স্কয়ার লেগের ওপর দিয়ে ছয় রান!",
                    "অবিশ্বাস্য শক্তি আর টাইমিং! বোলারের মাথার ওপর দিয়ে স্ট্রেইট বাউন্ডারি পার করলেন $striker, ছক্কা!",
                    "একেবারে মাঝব্যাটে লেগেছে! $bowler অসহায় চোখে দেখলেন বল আছড়ে পড়ল স্ট্যান্ডের ছাদে—৬ রান!",
                    "ক্রিজ ছেড়ে বেরিয়ে এসে বিধ্বংসী স্লগ! $striker এর ব্যাটে আরেকটি দর্শনীয় ওভার-বাউন্ডারি!",
                    "কী দারুণ ফ্লিক শট! রিস্টের কারিকুরিতে ডিপ মিড-উইকেট দিয়ে উড়িয়ে ছক্কা হাঁকালেন $striker!",
                    "দর্শকদের জন্য উপহার! $striker এর জোরালো সুইপে বল হারিয়ে গেল গ্যালারির ভিড়ে—ছয় রান!",
                    "এক পায়ে ভর করে চোখধাঁধানো আপার কাট! কিপার-ফিল্ডারের নাগালের অনেক ওপর দিয়ে ছক্কা!",
                    "গর্জে উঠল স্টেডিয়াম! $bowler এর সব পরিকল্পনা ভেস্তে দিয়ে $striker এর বাজপাখির মতো ছক্কা!",
                    "শট অব দ্য ডে! $striker এর নিখুঁত ব্যাটিং ঝড়ে বল উড়ে গেল সীমানার বহু ওপারে—ছয় রান!"
                ),
                categorySalt = 6
            )

            4 -> pickVariant(
                ball,
                listOf(
                    "চোখজুড়ানো কাভার ড্রাইভ! $striker এর চমৎকার টাইমিংয়ে ফিল্ডাররা শুধু তাকিয়েই দেখলেন—চার রান!",
                    "বুলেট গতির স্কয়ার কাট! ব্যাকওয়ার্ড পয়েন্টের পাশ দিয়ে বিদ্যুৎ বেগে বাউন্ডারি পার হলো বল!",
                    "ক্লাসিক স্ট্রেইট ড্রাইভ! $bowler এর হাত থেকে ছোড়া বল রকেটের মতো ফিরে গেল বোলারের পাশ দিয়ে—চার!",
                    "কব্জির দারুণ মোচড়ে গ্ল্যান্স! ফাইন লেগ ফিল্ডারকে পরাস্ত করে বল দৌড়ে গেল বাউন্ডারি লাইনে!",
                    "মিড-উইকেটের ওপর দিয়ে ঝড়ো পুল শট! গ্যাপ খুঁজে নিতে এক মুহূর্তও ভুল করলেন না $striker—৪ রান!",
                    "ব্যাটের কানায় লেগে স্লিপের পাশ দিয়ে দৌড়ে গেল বল! ভাগ্য আর সাহসে $striker পেলেন মূল্যবান চার!",
                    "অসাধারণ ইনসাইড-আউট শট! এক্সট্রা কাভার দিয়ে নাচতে নাচতে সীমানা পেরিয়ে গেল বল—বাউন্ডারি!",
                    "লেট কাটের জাদু! একদম শেষ মুহূর্তে ব্যাট ঘুরিয়ে থার্ডম্যান দিয়ে চার আদায় করলেন $striker!",
                    "শক্তি আর ছন্দের নিখুঁত মেলবন্ধন! অন-ড্রাইভে মিড-অনের নাগাল এড়িয়ে চমৎকার চার হাঁকালেন $striker!",
                    "ব্যাটে লাগতেই চারের সুর! $bowler এর ওপর চাপ বাড়িয়ে নিখুঁত গ্যাপ শটে ৪ রান তুলে নিলেন $striker!",
                    "আউটফিল্ড যেন কাঁচের মতো মসৃণ! বাউন্ডারি লাইন ছুঁয়ে জয়ধ্বনি তুলল $striker এর দুর্দান্ত শট—৪ রান!"
                ),
                categorySalt = 4
            )

            3 -> pickVariant(
                ball,
                listOf(
                    "দারুণ গ্যাপ শট ও চিতার মতো দৌড়! বাউন্ডারির কিনারা থেকে বল ফিরতে ফিরতেই ৩ রান পূর্ণ করলেন $striker!",
                    "ফিল্ডার সীমানার কাছে ছুটে বল থামালেন বটে, তবে ততক্ষণে উইকেটের মাঝে ঝড়ের গতিতে ৩ রান নিয়ে নিলেন ব্যাটাররা!",
                    "অফ-সাইডের ফাঁকা জায়গায় নিখুঁত প্লেসমেন্ট—তিনটি মূল্যবান রান যোগ হলো $striker ও দলীয় খাতায়!",
                    "আউটফিল্ড একটু মন্থর থাকায় চার হলো না, তবে দারুণ ফিটনেসের প্রমাণ দিয়ে ৩ রান ছিনিয়ে নিলেন $striker!",
                    "মিড-উইকেটের গভীর থেকে থ্রো আসার আগেই তিনবার ক্রিজ অদলবদল—অসাধারণ রানিং বিটুইন দ্য উইকেটস!",
                    "চমৎকার টাইমিং! বাউন্ডারির আগে বল আটকে গেলেও $bowler এর এই বলে সহজেই ৩ রান নিলেন $striker!",
                    "দৌড়ে চারের চেয়ে কম কী! দীর্ঘ আউটফিল্ড কাজে লাগিয়ে অনায়াসে ৩ রান পূর্ণ করলেন ব্যাটাররা!"
                ),
                categorySalt = 3
            )

            2 -> pickVariant(
                ball,
                listOf(
                    "চতুর ক্রিকেট! সিঙ্গেলকে কীভাবে ডাবলে রূপান্তর করতে হয় তা দেখিয়ে দিলেন $striker—২ রান!",
                    "গভীর গ্যাপে সফট হ্যান্ডে ঠেলে দিয়েই ২ রানের ডাক, ডাইভ দিয়ে নিরাপদে ক্রিজে পৌঁছালেন ব্যাটার!",
                    "উইকেটের মাঝে দারুণ বোঝাপড়া! ফিল্ডারের হালকা শিথিলতার সুযোগে চটপট ২ রান তুলে নিলেন $striker!",
                    "স্কয়ারের পেছনে আলতো ফ্লিক, দুই প্রান্তের ব্যাটারের দ্রুতগতিতে স্কোরবোর্ডে যোগ হলো ২ রান!",
                    "এক্সট্রা কাভারে সুনিয়ন্ত্রিত পুশ! প্রথম রান দ্রুত নিয়েই দ্বিতীয় রানের জন্য ছুটলেন $striker!",
                    "মিড-অনের পাশ দিয়ে ঠেলে দিয়ে ফিল্ডারকে চাপে ফেললেন $striker—অনায়াসে ২ রান!",
                    "ভালো লেংথের বল ছিল, তবে সঠিক জায়গায় পাঠিয়ে $bowler এর ওপর থেকে চাপ কমালেন ২ রান নিয়ে!",
                    "বোলিং প্রান্তে থ্রো আসার আগেই ক্রিজ দখল! $striker এর বুদ্ধিদীপ্ত ব্যাটিংয়ে এল ২ রান!",
                    "দুই রানের খিদে! বল পয়েন্টের দিকে পাঠিয়েই বিদ্যুৎ গতিতে দৌড়ে জোড়া রান পূর্ণ করলেন $striker!"
                ),
                categorySalt = 2
            )

            1 -> pickVariant(
                ball,
                listOf(
                    "নরম হাতে কভারের দিকে ঠেলে দিয়েই চোখের পলকে ১ রান ছিনিয়ে নিলেন $striker—দারুণ স্ট্রাইক রোটেশন!",
                    "মিড-অফের দিকে আলতো পুশ, এক মুহূর্ত দেরি না করে দ্রুত প্রান্ত বদল করলেন $striker!",
                    "স্কয়ার লেগে হালকা ফ্লিক করে ১ রান—বুদ্ধিদীপ্ত ব্যাটিংয়ে সচল রাখছেন স্কোরবোর্ড!",
                    "$bowler এর আঁটসাঁট ডেলিভারিতেও গ্যাপ খুঁজে বের করলেন $striker, সহজ সিঙ্গেল!",
                    "থার্ডম্যানের দিকে গাইড করে নিরাপদ ১ রান, নন-স্ট্রাইকারের হাতে স্ট্রাইক তুলে দিলেন $striker!",
                    "পায়ের ওপর আসা বল ফাইন লেগে পাঠিয়েই ১ রানের জন্য ছুট—চাপমুক্ত ব্যাটিং!",
                    "ডিফেন্সিভ মেজাজে খেললেও সিঙ্গেল বের করতে ভুললেন না $striker—১ রান যোগ হলো!",
                    "লং-অনের দিকে ড্রাইভ, বাউন্ডারি না এলেও দলের জন্য কার্যকরী ১ রান!",
                    "বোলারের কাছ দিয়ে ঠেলে দিয়েই চতুর সিঙ্গেল! ক্রমাগত স্ট্রাইক রোটেট করে ম্যাচের নিয়ন্ত্রণ ধরে রাখছেন!",
                    "এক রান নিয়েই শান্ত স্বস্তি! ডট বলের চাপ কাটিয়ে স্ট্রাইক বদল করলেন $striker।"
                ),
                categorySalt = 1
            )

            0 -> pickVariant(
                ball,
                listOf(
                    "অসাধারণ ডেলিভারি! $bowler এর সুইং আর গতির কাছে পরাস্ত $striker, বল বেরিয়ে গেল ব্যাটের কানা ঘেঁষে—ডট বল!",
                    "নিখুঁত ইয়র্কার! কোনোমতে ব্যাট নামিয়ে স্টাম্প বাঁচালেন $striker—দুর্দান্ত বোলিং প্রদর্শনী!",
                    "সতর্ক রক্ষণ! $bowler এর চমৎকার লাইন-লেন্থকে সম্মান জানিয়ে জমাট ডিফেন্স করলেন $striker!",
                    "তীক্ষ্ণ বাউন্সার! বুদ্ধিমত্তার সাথে মাথা নিচু করে বল ছেড়ে দিলেন $striker—রানের খাতা নড়ল না!",
                    "আগুন ঝরানো ডেলিভারি! পিচ থেকে সামান্য ভেতরে ঢোকা বল প্যাডের সামনে আটকে দিলেন $striker—ডট বল!",
                    "স্লোয়ার ডেলিভারিতে পুরোপুরি বোকা বনে গেলেন $striker, টাইমিং হলো না একদমই—কোনো রান নয়!",
                    "ড্রাইভ করার লোভ সামলাতে পারলেন না $striker, কিন্তু বল পরাস্ত করল আউটসাইড এজ—উইকেটকিপারের হাতে!",
                    "পিচ থেকে বাড়তি বাউন্স আদায় করলেন $bowler! জোরালো ডিফেন্স করলেও রান নেওয়ার সুযোগ পেলেন না $striker!",
                    "বোলার-ব্যাটারের তীব্র লড়াই! চাপ সৃষ্টি করে আরেকটি নিখুঁত ডট বল আদায় করলেন $bowler!",
                    "সোজা ব্যাটে মিড-অফের দিকে খেললেন $striker, কিন্তু ফিল্ডারের হাতে বল—অর্থবহ ডট বল!",
                    "লক্ষ্যভেদী লাইন! $bowler এর আঁটসাঁট বোলিংয়ে হাত খোলার কোনো সুযোগই পাচ্ছেন না $striker—ডট বল।"
                ),
                categorySalt = 5
            )

            else -> pickVariant(
                ball,
                listOf(
                    "ওভারথ্রো ও চমৎকার দৌড়ে স্কোরবোর্ডে যোগ হলো একসাথে ${ball.runsOffBat} রান—অসাধারণ তৎপরতা $striker এর!",
                    "দারুণ শটের পর অতিরিক্ত রানের বোনাস! এই বলে ${ball.runsOffBat} রান আদায় করে নিলেন $striker!"
                ),
                categorySalt = 7
            )
        }
    }

    private fun formatEnglishCommentary(
        ball: BallEvent,
        bowler: String,
        striker: String,
        dismissed: String,
        fielder: String
    ): String {
        if (ball.dismissalType == DismissalType.RETIRED_HURT) {
            return pickVariant(
                ball,
                listOf(
                    "RETIRED HURT! $dismissed walks off due to injury or discomfort (Not Out — eligible to return later).",
                    "Play pauses briefly as $dismissed retires hurt—not a wicket, and may resume the innings later.",
                    "$dismissed is forced to leave the crease retired hurt; hopeful signs for a return later on."
                ),
                categorySalt = 201
            )
        }

        if (ball.isWicket) {
            val runsNote = if (ball.totalRunsOnBall > 0) " (${ball.totalRunsOnBall} run${if (ball.totalRunsOnBall > 1) "s" else ""} scored)" else ""
            val wicketText = when (ball.dismissalType) {
                DismissalType.BOWLED -> pickVariant(
                    ball,
                    listOf(
                        "OUT! Clean bowled! $bowler rips through the gate and sends $dismissed's stumps cartwheeling!",
                        "TIMBER! An absolute jaffa from $bowler—searing yorker crashes straight into the base of off stump!",
                        "OUT! $dismissed plays all around a sharp nip-backer and $bowler shatters the woodwork!",
                        "BOWLED HIM! Pace, movement, and pinpoint accuracy from $bowler—$dismissed has to walk!",
                        "WHAT A DELIVERY! $bowler castles $dismissed with a peach that angled in and hit top of middle!",
                        "OUT! Big heave across the line by $dismissed, misses completely, and $bowler lights up the stumps!"
                    ),
                    categorySalt = 211
                )

                DismissalType.CAUGHT -> pickVariant(
                    ball,
                    listOf(
                        "OUT! Caught! $striker mistimes the lofted drive and $fielder settles safely underneath it—$bowler strikes!",
                        "WHAT A GRAB! $fielder dives full length to pull off a stunner—$bowler gets the huge breakthrough!",
                        "OUT! Straight into the trap! $dismissed slices $bowler high into the sky and $fielder makes no mistake!",
                        "GONE! Calm under pressure on the boundary rope by $fielder, and $dismissed departs off $bowler!",
                        "OUT! Slower delivery does the trick—$striker skies it off the toe-end and $fielder pockets a neat catch!",
                        "BREAKTHROUGH! $bowler outsmarts $dismissed with clever change of pace, taken cleanly by $fielder!"
                    ),
                    categorySalt = 212
                )

                DismissalType.CAUGHT_BEHIND -> pickVariant(
                    ball,
                    listOf(
                        "OUT! Edged and gone! A razor-sharp outside edge off $bowler is snapped up cleanly by $fielder!",
                        "CAUGHT BEHIND! Lovely corridor bowling by $bowler forces the nick, and $fielder takes a sharp catch!",
                        "OUT! Faint feather off the bat and $fielder dives behind the timbers to dismiss $dismissed!",
                        "GONE! Tempted outside off by $bowler, $dismissed pokes at it and $fielder does the rest!",
                        "OUT! Instant appeal and the finger goes up—$bowler finds the edge and $fielder hangs on!"
                    ),
                    categorySalt = 213
                )

                DismissalType.LBW -> pickVariant(
                    ball,
                    listOf(
                        "OUT! Plumb in front! $bowler traps $dismissed dead on the crease—up goes the umpire's finger!",
                        "LBW! Trapped in front by a darting delivery from $bowler, and $dismissed has to make the long walk!",
                        "OUT! Big shout from $bowler and the umpire agrees right away—hitting middle stump all day!",
                        "STONE DEAD LBW! $dismissed plays across the line to $bowler and is pinned right in front!",
                        "OUT! Pinpoint length from $bowler beats the bat and crashes onto the front pad—huge wicket!"
                    ),
                    categorySalt = 214
                )

                DismissalType.RUN_OUT -> pickVariant(
                    ball,
                    listOf(
                        "OUT! Run out! A bullet throw from $fielder catches $dismissed well short of the crease!",
                        "TERRIBLE MIX-UP! Both batters end up stranded and $fielder clinically removes the bails—$dismissed is out!",
                        "LIGHTNING FIELDING! Despite a desperate full-stretch dive, $dismissed falls short against $fielder's rocket throw!",
                        "OUT! Sharp pick-up and direct hit from $fielder! Unselfish risk turns fatal for $dismissed!",
                        "RUN OUT! Nerves of steel from $fielder in the ring, whipping the bails off in a flash!"
                    ),
                    categorySalt = 215
                )

                DismissalType.STUMPED -> pickVariant(
                    ball,
                    listOf(
                        "OUT! Stumped! $bowler beats the advancing $dismissed in flight and $fielder whips the bails off in a flash!",
                        "BLISTERING GLOVEWORK! $dismissed drags the back foot just an inch outside and $fielder completes the stumping!",
                        "OUT! Cleverly tossed up wider by $bowler—$dismissed misses the big swing and $fielder strikes behind the stumps!",
                        "SMART COMBO! $bowler deceives $dismissed with spin and drift, and $fielder finishes the job!",
                        "STUMPED! Quick as lightning behind the woodwork by $fielder—$dismissed can't get the boot back in time!"
                    ),
                    categorySalt = 216
                )

                DismissalType.HIT_WICKET -> pickVariant(
                    ball,
                    listOf(
                        "OUT! Hit wicket! Trying to pull $bowler from deep in the crease, $dismissed knocks the bails off!",
                        "DISASTER! $dismissed loses balance while swaying away and clips the stumps—out hit wicket!",
                        "UNBELIEVABLE! $dismissed accidentally crushes the woodwork on the follow-through off $bowler!",
                        "HEARTBREAK FOR THE BATTER! Deep in the crease to $bowler, $dismissed dislodges the bails for a hit wicket!"
                    ),
                    categorySalt = 217
                )

                DismissalType.MANKAD -> pickVariant(
                    ball,
                    listOf(
                        "OUT! Mankaded! $bowler spots $dismissed backing up too far and clips the bails at the non-striker's end!",
                        "DRAMA IN THE MIDDLE! $dismissed leaves the crease early and $bowler completes the non-striker run out!",
                        "OUT! Caught napping at the non-striker's end—$bowler stays alert and sends $dismissed back!"
                    ),
                    categorySalt = 218
                )

                DismissalType.RETIRED_OUT -> pickVariant(
                    ball,
                    listOf(
                        "RETIRED OUT! $dismissed walks off permanently as part of a tactical switch (Bowler: $bowler).",
                        "Tactical call from the dugout—$dismissed retires out and heads straight back to the pavilion.",
                        "RETIRED OUT! $dismissed steps aside permanently to bring in fresh firepower."
                    ),
                    categorySalt = 219
                )

                DismissalType.TIMED_OUT -> "OUT! Timed out! $dismissed fails to take guard within the allotted time!"
                DismissalType.OBSTRUCTING_FIELD -> "OUT! $dismissed is given out for obstructing the field!"
                DismissalType.HIT_BALL_TWICE -> "OUT! Rare dismissal—$dismissed is out for hitting the ball twice!"
                DismissalType.HANDLED_BALL -> "OUT! $dismissed departs for handling the ball!"

                else -> pickVariant(
                    ball,
                    listOf(
                        "OUT! Massive strike by $bowler as $dismissed heads back to the pavilion!",
                        "WICKET! $bowler breaks through and cuts short $dismissed's stay at the crease!"
                    ),
                    categorySalt = 220
                )
            }
            return wicketText + runsNote
        }

        if (ball.extraType == ExtraType.NO_BALL) {
            val total = ball.totalRunsOnBall
            return when {
                ball.runsOffBat == 6 -> pickVariant(
                    ball,
                    listOf(
                        "NO BALL AND LAUNCHED FOR SIX! $striker punishes the overstep into the stands ($total runs) — Free Hit next!",
                        "DOUBLE BLOW! $bowler oversteps and $striker dispatches it miles over long-on—$total runs and a Free Hit to come!",
                        "BRUTAL! $striker deposits the no-ball high over the ropes ($total runs), plus a Free Hit waiting next!"
                    ),
                    categorySalt = 221
                )

                ball.runsOffBat == 4 -> pickVariant(
                    ball,
                    listOf(
                        "NO BALL CRACKED FOR FOUR! $striker cashes in immediately ($total runs) and earns a Free Hit next ball!",
                        "OVERSTEP PUNISHED! Glorious boundary off a no-ball by $striker ($total runs) — Free Hit coming up!",
                        "FOUR PLUS NO BALL! $bowler leaks $total runs and still faces a Free Hit delivery next!"
                    ),
                    categorySalt = 222
                )

                ball.runsOffBat > 0 -> pickVariant(
                    ball,
                    listOf(
                        "NO BALL! $bowler oversteps the popping crease—$total runs added and a Free Hit awaits!",
                        "FREE HIT INCOMING! Batters scramble runs off the no-ball for $total total runs!",
                        "Costly error from $bowler! No-ball yields $total runs and sets up a thrilling Free Hit!"
                    ),
                    categorySalt = 223
                )

                else -> pickVariant(
                    ball,
                    listOf(
                        "NO BALL! Front foot well over the line by $bowler—+${ball.extraRuns} extra and a Free Hit next!",
                        "ALARM BELLS FOR $bowler! Umpire calls no-ball; ${ball.extraRuns} extra added and Free Hit coming up!",
                        "Overstepped! $bowler gifts ${ball.extraRuns} extra run(s) and hands $striker a golden Free Hit!",
                        "NO BALL signaled! Pressure mounts on $bowler as $striker gets ready to tee off on the Free Hit!",
                        "That's a no-ball! ${ball.extraRuns} bonus run(s) on the board and a mouth-watering Free Hit up next!"
                    ),
                    categorySalt = 224
                )
            }
        }

        if (ball.extraType == ExtraType.WIDE) {
            return if (ball.extraRuns >= 5) {
                pickVariant(
                    ball,
                    listOf(
                        "FIVE WIDES! Wayward down the leg side by $bowler, beats everyone and races to the rope (+${ball.extraRuns})!",
                        "BOUNDARY WIDES! Completely loses radar, beats the diving keeper, and gifts ${ball.extraRuns} extras!",
                        "Frustration for $bowler! Wild delivery speeds past the keeper for ${ball.extraRuns} wide runs!"
                    ),
                    categorySalt = 225
                )
            } else if (ball.extraRuns > 1) {
                pickVariant(
                    ball,
                    listOf(
                        "Wide and batters pinch extra runs too! ${ball.extraRuns} valuable extras added against $bowler!",
                        "$bowler sprays it off target and batters scamper across—+${ball.extraRuns} wide runs!"
                    ),
                    categorySalt = 226
                )
            } else {
                pickVariant(
                    ball,
                    listOf(
                        "Wide! $bowler drifts well down the leg side, +${ball.extraRuns} extra.",
                        "Too wide outside off stump—umpire stretches the arms as $bowler concedes +${ball.extraRuns}.",
                        "Loss of control there from $bowler, strays wide of the tramlines (+${ball.extraRuns} wide).",
                        "Freebie for the batting side! $bowler misses the target and gives away +${ball.extraRuns} wide.",
                        "Attempted wide yorker misses the marker—easy +${ball.extraRuns} run without lifting the bat."
                    ),
                    categorySalt = 227
                )
            }
        }

        if (ball.extraType == ExtraType.BYE) {
            return pickVariant(
                ball,
                listOf(
                    "BYES! Beats both bat and keeper—batters alertly cross for ${ball.extraRuns} bye run(s)!",
                    "Sharp awareness! Ball sneaks past the keeper and they sprint ${ball.extraRuns} bye(s).",
                    "Extra bounce deceives the keeper; ${ball.extraRuns} bonus bye run(s) on the scoreboard.",
                    "No bat involved, yet swift calling brings ${ball.extraRuns} bye run(s) to the total!"
                ),
                categorySalt = 228
            )
        }

        if (ball.extraType == ExtraType.LEG_BYE) {
            return pickVariant(
                ball,
                listOf(
                    "LEG BYE! Clips the pads and rolls into space—batters jog through for ${ball.extraRuns} run(s).",
                    "Deflected off the thigh pad into the vacant region for ${ball.extraRuns} leg-bye run(s).",
                    "Missed the flick, off the pad, and ${ball.extraRuns} leg-bye added to the tally.",
                    "Sharp inward seam hits the pad and squirts away—${ball.extraRuns} leg-bye run(s) taken."
                ),
                categorySalt = 229
            )
        }

        if (ball.extraType == ExtraType.PENALTY) {
            return pickVariant(
                ball,
                listOf(
                    "PENALTY RUNS! ${ball.extraRuns} runs awarded to the batting side for a rule infringement!",
                    "Huge bonus! Umpire signals ${ball.extraRuns} penalty runs to the batting team.",
                    "Costly fielding breach—${ball.extraRuns} penalty runs added straight to the total!"
                ),
                categorySalt = 230
            )
        }

        return when (ball.runsOffBat) {
            6 -> pickVariant(
                ball,
                listOf(
                    "MONSTER SIX! $striker clears the front leg and launches $bowler high into the upper tier!",
                    "BOOM! Clean as a whistle! $striker deposits $bowler way back over long-on for a maximum!",
                    "INTO THE STANDS! Ferocious pull shot by $striker sends the crowd into a frenzy—SIX runs!",
                    "STRAIGHT AS AN ARROW! $striker hammers it right back over $bowler's head—magnificent six!",
                    "SWEET SPOT! $bowler can only watch in awe as $striker muscles it clean over deep mid-wicket!",
                    "DANCING DOWN THE TRACK! $striker reaches the pitch and belts a towering maximum!",
                    "PURE POWER! $striker picks up the length in a flash and flicks it effortlessly over square leg for six!",
                    "AUDACIOUS UPPER CUT! $striker rides the bounce and sails it clean over third man—SIX!",
                    "CROWD PLEASER! $striker unleashes a blistering slog sweep and clears the ropes with yards to spare!"
                ),
                categorySalt = 206
            )

            4 -> pickVariant(
                ball,
                listOf(
                    "GLORIOUS COVER DRIVE! $striker leans in with textbook poise and threads the gap for FOUR!",
                    "CRACKING SQUARE CUT! $striker flashes hard outside off and the ball races past point in a blink!",
                    "BULLET STRAIGHT DRIVE! $bowler ducks out of the way as $striker drills it down the ground for FOUR!",
                    "SILKY WRISTWORK! $striker flicks off the pads past fine leg—racing away to the fence!",
                    "FEROCIOUS PULL! $striker swivels sharply and slams $bowler through mid-wicket for a boundary!",
                    "DELICATE LATE CUT! $striker waits till the last split second and guides it past slip for FOUR!",
                    "INSIDE-OUT CLASS! $striker skips down and lofts it handsomely over extra cover—one bounce four!",
                    "EDGED AND FOUR! Thick outside edge flies safely past the slips—lucky break, four runs to $striker!",
                    "PIERCING THE RING! $striker punches on the rise off the back foot and finds the rope with ease!"
                ),
                categorySalt = 204
            )

            3 -> pickVariant(
                ball,
                listOf(
                    "SUPERB PLACEMENT & SPRINT! Outfield rescue keeps it to 3, brilliant running by $striker!",
                    "Hard push into the deep cover gap—batters push hard from the first step and complete 3 runs!",
                    "Timed nicely off the pads; deep fielder pulls it back inside the rope as they sprint 3!",
                    "Tireless running between the wickets! $striker turns good placement into 3 valuable runs.",
                    "Just holds up before the cushion—three crisp runs added thanks to rapid calling!"
                ),
                categorySalt = 203
            )

            2 -> pickVariant(
                ball,
                listOf(
                    "SMART CRICKET! $striker nudges into deep pocket and sprints back aggressively for 2 runs!",
                    "Soft hands and rapid calling—$striker turns a routine single into a comfortable brace!",
                    "Great hustle between the wickets! $striker dives home safely to complete 2 runs off $bowler.",
                    "Tucked neatly behind square leg, putting the deep fielder under pressure for a clean 2 runs.",
                    "Controlled push through extra cover; two steps of intent bring 2 more to the board.",
                    "Alert running! $striker exploits the big outfield gap to pinch a brisk 2 runs."
                ),
                categorySalt = 202
            )

            1 -> pickVariant(
                ball,
                listOf(
                    "TAP AND RUN! $striker drops it with soft hands into cover and steals a brisk single!",
                    "Smart strike rotation—$striker glides $bowler down to third man for an effortless single.",
                    "Whipped smoothly off the hips to deep square leg for 1 easy run.",
                    "Punched firmly to mid-off's left; quick feet ensure $striker gets off strike cleanly.",
                    "Solid control—$striker guides it into the leg-side gap and rotates the strike with 1 run.",
                    "Busy at the crease! $striker keeps the scoreboard ticking with a tidy single off $bowler.",
                    "Tight delivery from $bowler, yet $striker still finds an angle to squeeze out 1 run!"
                ),
                categorySalt = 201
            )

            0 -> pickVariant(
                ball,
                listOf(
                    "RIPPER! $bowler beats the outside edge by a whisker—deadly seam movement, dot ball!",
                    "PINPOINT YORKER! $striker jams the bat down just in time to keep the stumps intact—dot ball!",
                    "ROCK-SOLID DEFENSE! $striker gets right behind the line of $bowler's sharp delivery—no run.",
                    "FIERY BOUNCER! $striker sways cleanly out of harm's way as the ball zips through to the keeper.",
                    "DECEIVED IN PACE! Slower off-cutter from $bowler leaves $striker swinging early—dot ball!",
                    "HIGH-CLASS CONTEST! $bowler hits a probing length and forces a watchful block from $striker.",
                    "STIFLING PRESSURE! $striker drives firmly but straight into the ring fielder's hands—no run.",
                    "EXTRA BOUNCE! Sharp lift off a good length surprises $striker, who defends awkwardly—dot ball.",
                    "TIGHT LINES! $bowler refuses to give any width, building pressure with another clinical dot ball!"
                ),
                categorySalt = 205
            )

            else -> pickVariant(
                ball,
                listOf(
                    "CHAOS IN THE FIELD! Overthrows and tireless sprinting bring ${ball.runsOffBat} runs to $striker!",
                    "Massive reward for hustle—${ball.runsOffBat} runs added to the total off that delivery!"
                ),
                categorySalt = 207
            )
        }
    }
}
