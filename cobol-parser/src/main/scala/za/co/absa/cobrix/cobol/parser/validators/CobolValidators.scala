/*
 * Copyright 2018 ABSA Group Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package za.co.absa.cobrix.cobol.parser.validators

import za.co.absa.cobrix.cobol.parser.ast.datatype.{AlphaNumeric, CobolType, Decimal, Integral}
import za.co.absa.cobrix.cobol.parser.common.Constants
import za.co.absa.cobrix.cobol.parser.exceptions.SyntaxErrorException

object CobolValidators {

  def validatePic(lineNumber: Int, fieldName: String, pic: String): Unit = {
    val displayPic = pic.replaceAll("\\,", ".")

    def throwError(msg: String): Unit = {
      throw new SyntaxErrorException(lineNumber, fieldName, s"Invalid 'PIC $displayPic'. " + msg)
    }

    object State extends Enumeration {
      type State = Value
      val INITIAL, SIGN, STRING, NUMBER, OPEN_BRACKET, NUMBER_IN_BRACKET, CLOSING_BRACKET, DECIMAL_POINT = Value
    }

    import State._

    var state = INITIAL
    var signEncountered = false
    var decimalEncountered = false
    var isNumber = false
    var numOpenedBrackets = 0
    var numberInBrackets = ""
    var i = 1
    while (i <= pic.length) {
      val c = displayPic.charAt(i - 1)
      if (state != NUMBER_IN_BRACKET && state != OPEN_BRACKET &&
        c != 'X' && c != 'A' && c != '9' && c != 'S' && c != 'V' && c != '.' && c != '(' && c != ')') {
        throwError(s"Invalid character encountered: '$c' at position $i")
      }

      state match {
        case INITIAL =>
          c match {
            case 'X' =>
              isNumber = false
              state = STRING
            case 'A' =>
              isNumber = false
              state = STRING
            case '9' =>
              isNumber = true
              state = NUMBER
            case 'S' =>
              isNumber = true
              signEncountered = true
              state = SIGN
            case '.' =>
              state = DECIMAL_POINT
              decimalEncountered = true
            case 'V' =>
              state = DECIMAL_POINT
              decimalEncountered = true
            case ch => throwError(s"A PIC cannot start with '$ch'.")
          }
        case SIGN =>
          c match {
            case '9' =>
              state = NUMBER
            case ch => throwError(s"Unexpected character '$ch' at position $i. A sign definition should be followed by a number definition.")
          }
        case STRING =>
          c match {
            case 'X' =>
              state = STRING
            case 'A' =>
              state = STRING
            case '(' =>
              state = OPEN_BRACKET
              numOpenedBrackets += 1
              if (numOpenedBrackets > 1)
                throwError(s"Only one level of brackets nesting is allowed at position $i.")
            case ')' =>
              throwError(s"Closing bracked doesn't have matching open one at position $i.")
            case '9' =>
              throwError(s"Cannot mix 'X','A' and '9' at position $i.")
            case 'S' =>
              throwError(s"A sign 'S' can only be specified for numeric fields at position $i.")
            case '.' =>
              throwError(s"A decimal point '.' can only be specified for numeric fields at position $i.")
            case 'V' =>
              throwError(s"A decimal point 'V' can only be specified for numeric fields at position $i.")
            case ch => throwError(s"Unexpected character '$ch' at position $i.")
          }
        case NUMBER =>
          c match {
            case '9' =>
              state = NUMBER
            case 'A' =>
              throwError(s"Cannot mix '9' with 'A' at position $i.")
            case 'X' =>
              throwError(s"Cannot mix '9' with 'X' at position $i.")
            case '(' =>
              state = OPEN_BRACKET
              numOpenedBrackets += 1
              if (numOpenedBrackets > 1)
                throwError(s"Only one level of brackets nesting is allowed at position $i.")
            case ')' =>
              throwError(s"Closing bracket doesn't have matching open one at position $i.")
            case '.' =>
              state = DECIMAL_POINT
              if (decimalEncountered) {
                throwError(s"Decimal point '.' should be specified only once at position $i.")
              }
              decimalEncountered = true
            case 'V' =>
              state = DECIMAL_POINT
              if (decimalEncountered) {
                throwError(s"Decimal point 'V' should be specified only once at position $i.")
              }
              decimalEncountered = true
            case 'S' =>
              throwError(s"A sign should be specified only once at position $i.")
            case ch => throwError(s"Unexpected character '$ch' at position $i.")
          }
        case OPEN_BRACKET =>
          c match {
            case a if a.toByte >= '0'.toByte && a.toByte <= '9'.toByte =>
              state = NUMBER_IN_BRACKET
              numberInBrackets = s"$a"
            case ')' =>
              throwError(s"There should be a number inside parenthesis at position $i.")
            case ch => throwError(s"Unexpected character '$ch' at position $i.")
          }
        case NUMBER_IN_BRACKET =>
          c match {
            case a if a.toByte >= '0'.toByte && a.toByte <= '9'.toByte =>
              state = NUMBER_IN_BRACKET
              numberInBrackets += a
            case ')' =>
              numOpenedBrackets -= 1
              if (numberInBrackets.length > 5) {
                throwError(s"The number inside parenthesis is too big at position ${i - 1}.")
              }
              state = CLOSING_BRACKET
            case ch => throwError(s"Unexpected character '$ch' at position $i.")
          }
        case CLOSING_BRACKET =>
          c match {
            case '9' =>
              state = NUMBER
              if (!isNumber) {
                throwError(s"Cannot mix '9' with 'A' or 'X' at position $i.")
              }
            case 'V' =>
              state = DECIMAL_POINT
              if (!isNumber) {
                throwError(s"Cannot specify 'V' for non-numeric fields at position $i.")
              }
              if (decimalEncountered) {
                throwError(s"A Decimal point 'V' or '.' should be specified only once at position $i.")
              }
              decimalEncountered = true
            case '.' =>
              state = DECIMAL_POINT
              if (!isNumber) {
                throwError(s"Cannot specify '.' for non-numeric fields at position $i.")
              }
              if (decimalEncountered) {
                throwError(s"A Decimal point 'V' or '.' should be specified only once at position $i.")
              }
              decimalEncountered = true
            case 'A' =>
              state = STRING
              if (isNumber) {
                throwError(s"Cannot mix 'A' with '9' at position $i.")
              }
            case 'X' =>
              state = STRING
              if (isNumber) {
                throwError(s"Cannot mix 'X' with '9' at position $i.")
              }
            case ch => throwError(s"Unexpected character '$ch' at position $i.")
          }
        case DECIMAL_POINT =>
          c match {
            case '9' =>
              state = NUMBER
            case 'A' =>
              throwError(s"Cannot mix 'A' with '9' at position $i.")
            case 'X' =>
              throwError(s"Cannot mix 'X' with '9' at position $i.")
            case 'V' =>
              throwError(s"Redundant decimal point character '$c' at position $i.")
            case '.' =>
              throwError(s"Redundant decimal point character '$c' at position $i.")
            case ch => throwError(s"Unexpected character '$ch' at position $i.")
          }
      }

      i += 1
    }

    // Validate final state
    state match {
      case INITIAL => throwError("A PIC cannot be empty")
      case SIGN => throwError("A number precision and scale should follow 'S'.")
      case DECIMAL_POINT => // Seems this is ok // throwError("A scale must be specified after the decimal point.")
      case OPEN_BRACKET => throwError("An opening parenthesis cannot be the ast character of a PIC.")
      case NUMBER_IN_BRACKET => throwError("The PIC definition is not finished. Missing closing bracket at the end.")
      case _ => // OK
    }

    if (numOpenedBrackets != 0) {
      throwError("Parenthesis don't match.")
    }


  }

  def validateDataType(lineNumber: Int, fieldName: String, dt: CobolType): Unit = {
    dt match {
      case a: AlphaNumeric => // no vaidation needed for a string
      case i: Integral => validateIntegralType(lineNumber, fieldName, i)
      case d: Decimal => validateDecimalType(lineNumber, fieldName, d)
    }
  }

  def validateIntegralType(lineNumber: Int, fieldName: String, dt: Integral): Unit = {
    if (dt.isSignSeparate && dt.compact.isDefined) {
      throw new SyntaxErrorException(lineNumber, fieldName, s"SIGN SEPARATE clause is not supported for COMP-${dt.compact.get}. It is only supported for DISPLAY formatted fields.")
    }
    for (bin <- dt.compact) {
      if (dt.precision > Constants.maxBinIntPrecision) {
        throw new SyntaxErrorException(lineNumber, fieldName,
          s"BINARY-encoded integers with precision bigger than ${Constants.maxBinIntPrecision} are not supported.")
      }
    }
  }

  def validateDecimalType(lineNumber: Int, fieldName: String, dt: Decimal): Unit = {
    val displayPic = dt.pic.replaceAll("\\,", ".")
    if (dt.explicitDecimal && dt.compact.isDefined) {
      throw new SyntaxErrorException(lineNumber, fieldName,
        s"Explicit decimal point in 'PIC $displayPic' is not supported for COMP-${dt.compact.get}. It is only supported for DISPLAY formatted fields.")
    }
    if (dt.isSignSeparate && dt.compact.isDefined) {
      throw new SyntaxErrorException(lineNumber, fieldName, s"SIGN SEPARATE clause is not supported for COMP-${dt.compact.get}. It is only supported for DISPLAY formatted fields.")
    }
    if (dt.precision - dt.scale > Constants.maxDecimalPrecision) {
      throw new SyntaxErrorException(lineNumber, fieldName,
        s"Decimal numbers with precision bigger than ${Constants.maxDecimalPrecision} are not supported.")
    }
    if (dt.scale > Constants.maxDecimalScale) {
      throw new SyntaxErrorException(lineNumber, fieldName,
        s"Decimal numbers with scale bigger than ${Constants.maxDecimalScale} are not supported.")
    }
  }

}
