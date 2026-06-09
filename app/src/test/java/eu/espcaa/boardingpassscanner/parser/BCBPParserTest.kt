package eu.espcaa.boardingpassscanner.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BCBPParserTest {
    @Test
    fun parsesSingleLegPayloadAtMandatoryLength() {
        val rawData = bcbp(
            numberOfLegs = 1,
            legs = listOf(
                leg(
                    pnr = "ABC1234",
                    from = "FCO",
                    to = "JFK",
                    carrier = "AZ ",
                    flightNumber = "00610",
                    julianDate = "180",
                    compartment = "Y",
                    seat = "12A ",
                    sequence = "00042"
                )
            )
        )

        val result = ParseBCBP(rawData)

        assertTrue(result.errors.toString(), result.errors.isEmpty())
        val pass = result.boardingPass
        assertNotNull(pass)
        assertEquals(58, rawData.length)
        assertEquals(1, pass!!.numberOfLegs)
        assertEquals("ABC1234", pass.pnrCode)
        assertEquals("FCO", pass.legs[0].from)
        assertEquals("JFK", pass.legs[0].to)
        assertEquals("AZ", pass.legs[0].carrier)
        assertEquals("00610", pass.legs[0].flightNumber)
        assertEquals("12A", pass.legs[0].seat)
        assertEquals("00042", pass.legs[0].sequenceNumber)
    }

    @Test
    fun parsesMultipleMandatoryLegsWithoutHexLengthField() {
        val rawData = bcbp(
            numberOfLegs = 2,
            legs = listOf(
                leg(
                    pnr = "ABC1234",
                    from = "FCO",
                    to = "CDG",
                    carrier = "AF ",
                    flightNumber = "01234",
                    julianDate = "181",
                    compartment = "Y",
                    seat = "14C ",
                    sequence = "00008"
                ),
                leg(
                    pnr = "ZZ98765",
                    from = "CDG",
                    to = "JFK",
                    carrier = "DL ",
                    flightNumber = "00045",
                    julianDate = "182",
                    compartment = "J",
                    seat = "02A ",
                    sequence = "00123"
                )
            )
        )

        val result = ParseBCBP(rawData)

        assertTrue(result.errors.toString(), result.errors.isEmpty())
        val pass = result.boardingPass
        assertNotNull(pass)
        assertEquals(2, pass!!.numberOfLegs)
        assertEquals(2, pass.legs.size)
        assertEquals("FCO", pass.legs[0].from)
        assertEquals("CDG", pass.legs[0].to)
        assertEquals("CDG", pass.legs[1].from)
        assertEquals("JFK", pass.legs[1].to)
        assertEquals("DL", pass.legs[1].carrier)
        assertEquals("00045", pass.legs[1].flightNumber)
        assertEquals("02A", pass.legs[1].seat)
        assertEquals("00123", pass.legs[1].sequenceNumber)
    }

    private fun bcbp(numberOfLegs: Int, legs: List<String>): String {
        require(numberOfLegs == legs.size)
        return "M" +
                numberOfLegs +
                "DOE/JOHN".padEnd(20, ' ') +
                "E" +
                legs.joinToString(separator = "")
    }

    private fun leg(
        pnr: String,
        from: String,
        to: String,
        carrier: String,
        flightNumber: String,
        julianDate: String,
        compartment: String,
        seat: String,
        sequence: String,
        passengerStatus: String = "0"
    ): String {
        val rawLeg = pnr +
                from +
                to +
                carrier +
                flightNumber +
                julianDate +
                compartment +
                seat +
                sequence +
                passengerStatus

        require(rawLeg.length == 35)
        return rawLeg
    }
}
