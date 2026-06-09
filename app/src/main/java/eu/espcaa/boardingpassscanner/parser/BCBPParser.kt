package eu.espcaa.boardingpassscanner.parser

import java.time.LocalDate

// interesting :p https://www.iata.org/contentassets/1dccc9ed041b4f3bbdcf8ee8682e75c4/2021_03_02-bcbp-implementation-guide-version-7-.pdf

data class JulianBoardingPass(
    val numberOfLegs: Int,       // index 1 :3
    val passengerName: String,   // mandatory, 20 chars
    val pnrCode: String,         // mandatory, 7 chars
    val legs: List<JulianLeg>,
    val isEticket: Boolean       // mandatory: 1 char ( 'E' for e-ticket, space for paper ticket)
)

data class JulianLeg(
    val from: String,            // mandatory: 3 chars ( iata airport code )
    val to: String,              // mandatory: 3 chars ( iata airport code )
    val carrier: String,         // mandatory: 3 chars ( iata airline code )
    val flightNumber: String,    // mandatory: 5 chars ( right justified, zero filled )
    val flightJulian: String,          // mandatory: 3-digit julian date
    val flightDateJulian: String, // mandatory: 3-digit julian date
    val seat: String,            // mandatory : 4 chars ( right justified, space filled )
    val sequenceNumber: String,  // mandatory: 5 chars ( right justified, zero filled )
    val compartmentCode: String,     // mandatory: 1 char cabin class
)

data class BoardingPass(
    val numberOfLegs: Int,       // index 1 :3
    val passengerName: String,   // mandatory, 20 chars
    val pnrCode: String,         // mandatory, 7 chars
    val issueYear: Int? = null,
    val legs: List<Leg>
)

data class Leg(
    val from: String,            // mandatory: 3 chars ( iata airport code )
    val to: String,              // mandatory: 3 chars ( iata airport code )
    val carrier: String,         // mandatory: 3 chars ( iata airline code )
    val flightNumber: String,    // mandatory: 5 chars ( right justified, zero filled )
    val flightJulian: String,          // mandatory: 3-digit julian date
    val flightDate: LocalDate?,       // derived from flightJulian, may be null if flightJulian is null or invalid
    val seat: String,            // mandatory : 4 chars ( right justified, space filled )
    val sequenceNumber: String,  // mandatory: 5 chars ( right justified, zero filled )
    val compartmentCode: String,     // mandatory: 1 char cabin class
    val isEticket: Boolean       // mandatory: 1 char ( 'E' for e-ticket, space for paper ticket)
)

data class Error(
    val message: String,
    val details: String? = null
)

data class BCBPParseResult(
    val boardingPass: JulianBoardingPass?,
    val errors: List<Error>
)

private const val UNIQUE_MANDATORY_LENGTH = 23
private const val MANDATORY_LEG_LENGTH = 35

private data class ParsedMandatoryLeg(
    val pnrCode: String,
    val leg: JulianLeg
)

private fun parseMandatoryLeg(rawData: String, start: Int): ParsedMandatoryLeg {
    val pnrCode = rawData.substring(start, start + 7).trim()
    val departure = rawData.substring(start + 7, start + 10)
    val arrival = rawData.substring(start + 10, start + 13)
    val carrier = rawData.substring(start + 13, start + 16).trim()
    val flightNumber = rawData.substring(start + 16, start + 21).trim()
    val flightJulian = rawData.substring(start + 21, start + 24)
    val compartmentCode = rawData[start + 24].toString()
    val seat = rawData.substring(start + 25, start + 29).trim()
    val sequence = rawData.substring(start + 29, start + 34).trim()

    return ParsedMandatoryLeg(
        pnrCode = pnrCode,
        leg = JulianLeg(
            from = departure,
            to = arrival,
            carrier = carrier,
            flightNumber = flightNumber,
            flightJulian = flightJulian,
            flightDateJulian = flightJulian,
            seat = seat,
            sequenceNumber = sequence,
            compartmentCode = compartmentCode,
        )
    )
}


fun ParseIATADate(julianDate: String, currentYear: Int = LocalDate.now().year): LocalDate? {
    return try {
        if (julianDate.length == 3) {
            LocalDate.ofYearDay(currentYear, julianDate.toInt())
        } else if (julianDate.length == 4) {
            val year = julianDate.substring(0, 1).toInt() + (currentYear / 10) * 10
            val dayOfYear = julianDate.substring(1).toInt()
            LocalDate.ofYearDay(year, dayOfYear)
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }
}

fun ParseBCBP(rawData: String): BCBPParseResult {
    val errors = mutableListOf<Error>()
    val legs = mutableListOf<JulianLeg>()

    try {

        if (rawData.length < UNIQUE_MANDATORY_LENGTH || rawData[0] != 'M') {
            errors.add(
                Error(
                    "Invalid format",
                    "Not enough characters for mandatory unique data or does not start with 'M', rawData: $rawData"
                )
            )
            return BCBPParseResult(null, errors)
        }

        val numberOfLegs = rawData[1].toString().toIntOrNull()?.takeIf { it in 1..4 } ?: run {
            errors.add(
                Error(
                    "Invalid number of legs",
                    "Expected a digit between 1 and 4 at position 1, got '${rawData[1]}'"
                )
            )
            return BCBPParseResult(null, errors)
        }

        val requiredLength = UNIQUE_MANDATORY_LENGTH + (numberOfLegs * MANDATORY_LEG_LENGTH)
        if (rawData.length < requiredLength) {
            errors.add(
                Error(
                    "Invalid format",
                    "Not enough characters for $numberOfLegs mandatory leg(s), expected at least $requiredLength, got ${rawData.length}"
                )
            )
            return BCBPParseResult(null, errors)
        }

        val passengerName = rawData.substring(2, 22).trim()
        val isEticket = rawData[22] == 'E'

        var pnrCode = ""
        var pointer = UNIQUE_MANDATORY_LENGTH

        repeat(numberOfLegs) { index ->
            val parsedLeg = parseMandatoryLeg(rawData, pointer)
            if (index == 0) {
                pnrCode = parsedLeg.pnrCode
            }
            legs.add(parsedLeg.leg)
            pointer += MANDATORY_LEG_LENGTH
        }

        val boardingPass = JulianBoardingPass(
            numberOfLegs = numberOfLegs,
            passengerName = passengerName,
            pnrCode = pnrCode,
            legs = legs,
            isEticket = isEticket
        )
        return BCBPParseResult(boardingPass, errors)
    } catch (e: Exception) {
        errors.add(Error("Parsing error", e.message ?: "Unknown error"))
        return BCBPParseResult(null, errors)
    }
}

fun ConvertToBoardingPass(
    julianPass: JulianBoardingPass,
    year: Int? = LocalDate.now().year
): BoardingPass {
    val legs = julianPass.legs.map { leg ->
        Leg(
            from = leg.from,
            to = leg.to,
            carrier = leg.carrier,
            flightNumber = leg.flightNumber,
            flightJulian = leg.flightJulian,
            flightDate = ParseIATADate(leg.flightJulian, year ?: LocalDate.now().year),
            seat = leg.seat,
            sequenceNumber = leg.sequenceNumber,
            compartmentCode = leg.compartmentCode,
            isEticket = julianPass.isEticket
        )
    }

    return BoardingPass(
        numberOfLegs = julianPass.numberOfLegs,
        passengerName = julianPass.passengerName,
        pnrCode = julianPass.pnrCode,
        issueYear = year,
        legs = legs
    )
}
