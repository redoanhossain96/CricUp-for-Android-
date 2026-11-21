package com.example

import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testMatchJsonDirectDeserialization() {
    val original = com.example.data.model.Match(
      id = "ZVGFA",
      matchCode = "ZVGFA",
      code = "ZVGFA",
      title = "Bangladesh vs India"
    )
    val json = com.example.util.JsonHelper.toJson(original)
    assertNotNull(json)
    val restored = com.example.util.JsonHelper.fromJson<com.example.data.model.Match>(json)
    assertNotNull(restored)
    assertEquals("ZVGFA", restored?.matchCode)
    assertEquals("Bangladesh vs India", restored?.title)
  }

  @Test
  fun testFirestoreDocumentDataDeserializationWithKeyFix() {
    val original = com.example.data.model.Match(
      id = "ZVGFA",
      matchCode = "ZVGFA",
      code = "ZVGFA",
      title = "Bangladesh vs India",
      isPublic = true
    )
    // Firestore maps isPublic to "public"
    val firestoreData = mutableMapOf<String, Any?>(
      "id" to "ZVGFA",
      "matchCode" to "ZVGFA",
      "code" to "ZVGFA",
      "title" to "Bangladesh vs India",
      "public" to true,
      "status" to "LIVE"
    )
    if (firestoreData.containsKey("public") && !firestoreData.containsKey("isPublic")) {
      firestoreData["isPublic"] = firestoreData["public"]
    }
    val mapType = com.squareup.moshi.Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    val adapter = com.example.util.JsonHelper.moshi.adapter<Map<String, Any?>>(mapType)
    val jsonString = adapter.toJson(firestoreData)
    val restored = com.example.util.JsonHelper.fromJson<com.example.data.model.Match>(jsonString)
    assertNotNull("Restored match must not be null!", restored)
    assertEquals("ZVGFA", restored?.matchCode)
    assertTrue(restored?.isPublic == true)
  }

  @Test
  fun testGroupBallEventsByOver_splitsMidOverBowlerSpells() {
    val events = listOf(
      com.example.data.model.BallEvent(
        id = "b1", matchId = "m1", inningsNumber = 1, overNumber = 0, ballInOver = 1,
        bowlerName = "Mustafizur", strikerName = "Kohli", runsOffBat = 1,
        isLegalDelivery = true, ballSeq = 1
      ),
      com.example.data.model.BallEvent(
        id = "b2", matchId = "m1", inningsNumber = 1, overNumber = 0, ballInOver = 2,
        bowlerName = "Taskin", strikerName = "Rohit", runsOffBat = 4,
        isLegalDelivery = true, ballSeq = 2
      ),
      com.example.data.model.BallEvent(
        id = "b3", matchId = "m1", inningsNumber = 1, overNumber = 1, ballInOver = 1,
        bowlerName = "Shakib", strikerName = "Rohit", runsOffBat = 0,
        isLegalDelivery = true, ballSeq = 3
      )
    )
    val grouped = com.example.ui.components.groupBallEventsByOver(events)
    assertEquals(2, grouped.size)
    // Sorted descending by overNumber
    assertEquals(2, grouped[0].overNumber)
    assertFalse(grouped[0].isSplitOver)
    assertEquals(1, grouped[1].overNumber)
    assertTrue(grouped[1].isSplitOver)
    assertEquals(2, grouped[1].bowlerSpells.size)
    assertEquals("Mustafizur", grouped[1].bowlerSpells[0].bowlerName)
    assertEquals("Taskin", grouped[1].bowlerSpells[1].bowlerName)
  }

  @Test
  fun testRetiredHurtAndRetiredOut_doNotCountLegalBalls() {
    val events = listOf(
      com.example.data.model.BallEvent(
        id = "b1", matchId = "m1", inningsNumber = 1, overNumber = 0, ballInOver = 1,
        bowlerName = "BowlerA", strikerName = "Batter1", runsOffBat = 1,
        isLegalDelivery = true, ballSeq = 1
      ),
      com.example.data.model.BallEvent(
        id = "b2", matchId = "m1", inningsNumber = 1, overNumber = 0, ballInOver = 1,
        bowlerName = "BowlerA", strikerName = "Batter1", runsOffBat = 0,
        isLegalDelivery = false, isWicket = false,
        dismissalType = com.example.data.model.DismissalType.RETIRED_HURT, ballSeq = 2
      ),
      com.example.data.model.BallEvent(
        id = "b3", matchId = "m1", inningsNumber = 1, overNumber = 0, ballInOver = 1,
        bowlerName = "BowlerA", strikerName = "Batter2", runsOffBat = 0,
        isLegalDelivery = false, isWicket = true,
        dismissalType = com.example.data.model.DismissalType.RETIRED_OUT, ballSeq = 3
      )
    )
    val grouped = com.example.ui.components.groupBallEventsByOver(events)
    assertEquals(1, grouped.size)
    // Only b1 counts as legal ball; RETIRED_HURT and RETIRED_OUT must NOT count as balls
    assertEquals(1, grouped[0].bowlerSpells[0].legalBalls)
    // Only RETIRED_OUT counts as wicket
    assertEquals(1, grouped[0].wickets)
    assertEquals("RH", events[1].displayBadgeText)
    assertEquals("RO", events[2].displayBadgeText)
  }
}
