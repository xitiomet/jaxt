package org.openstatic.aprs.parser;

import java.util.Date;
import java.util.regex.Pattern;

public class PositionParser
{
    private static Pattern commaSplit = Pattern.compile(",");

    public static Position parseUncompressed(byte[] msgBody, int cursor) throws Exception
    {
        Date date = new Date();
        if (msgBody[0] == '/' || msgBody[0] == '@')
        {
            if (msgBody[cursor+6] == 'z') {
                int day    = (msgBody[cursor+0] - '0') * 10 + msgBody[cursor+1] - '0';
                int hour   = (msgBody[cursor+2] - '0') * 10 + msgBody[cursor+3] - '0';
                int minute = (msgBody[cursor+4] - '0') * 10 + msgBody[cursor+5] - '0';
                date.setDate(day);
                date.setHours(hour);
                date.setMinutes(minute);
            }
            cursor += 7;
        }
        if (msgBody.length < cursor + 19) {
            throw new UnparsablePositionException("Uncompressed packet too short");
        }

        int positionAmbiguity = 0;
        char[] posbuf = new char[msgBody.length - cursor + 1];
        int pos = 0;
        for (int i = cursor; i < cursor + 19; i++) {
            posbuf[pos] = (char) msgBody[i];
            pos++;
        }

        if (posbuf[2] == ' ') {
            posbuf[2] = '3';
            posbuf[3] = '0';
            posbuf[5] = '0';
            posbuf[6] = '0';
            positionAmbiguity = 1;
        }
        if (posbuf[3] == ' ') {
            posbuf[3] = '5';
            posbuf[5] = '0';
            posbuf[6] = '0';
            positionAmbiguity = 2;
        }
        if (posbuf[5] == ' ') {
            posbuf[5] = '5';
            posbuf[6] = '0';
            positionAmbiguity = 3;
        }
        if (posbuf[6] == ' ') {
            posbuf[6] = '5';
            positionAmbiguity = 4;
        }
        // longitude
        if (posbuf[12] == ' ') {
            posbuf[12] = '3';
            posbuf[13] = '0';
            posbuf[15] = '0';
            posbuf[16] = '0';
            positionAmbiguity = 1;
        }
        if (posbuf[13] == ' ') {
            posbuf[13] = '5';
            posbuf[15] = '0';
            posbuf[16] = '0';
            positionAmbiguity = 2;
        }
        if (posbuf[15] == ' ') {
            posbuf[15] = '5';
            posbuf[16] = '0';
            positionAmbiguity = 3;
        }
        if (posbuf[16] == ' ') {
            posbuf[16] = '5';
            positionAmbiguity = 4;
        }

        try {
            double latitude = parseDegMin(posbuf, 0, 2, 7, true);
            char lath = (char) posbuf[7];
            char symbolTable = (char) posbuf[8];
            double longitude = parseDegMin(posbuf, 9, 3, 8, true);
            char lngh = (char) posbuf[17];
            char symbolCode = (char) posbuf[18];

            if (lath == 's' || lath == 'S')
                latitude = 0.0F - latitude;
            else if (lath != 'n' && lath != 'N')
                throw new Exception("Bad latitude sign character");

            if (lngh == 'w' || lngh == 'W')
                longitude = 0.0F - longitude;
            else if (lngh != 'e' && lngh != 'E')
                throw new Exception("Bad longitude sign character");
            Position position = new Position(latitude, longitude, positionAmbiguity, symbolTable, symbolCode);
            position.setTimestamp(date);
            return position;
        } catch (Exception e) {
            throw new Exception(e);
        }
    }

    public static Position parseUncompressed(byte[] msgBody) throws Exception
    {
        return parseUncompressed(msgBody, 1);
    }

    public static DataExtension parseUncompressedExtension(byte[] msgBody, int cursor) throws Exception
    {
        DataExtension de = null;

        if (msgBody.length <= 18 + cursor) {
            return null;
        }
        if ((char) msgBody[19 + cursor] == 'P' && (char) msgBody[20 + cursor] == 'H'
                && (char) msgBody[21 + cursor] == 'G') {
            PHGExtension phg = new PHGExtension();
            try {
                phg.setPower(Integer.parseInt(new String(msgBody, 22 + cursor, 1)));
                phg.setHeight(Integer.parseInt(new String(msgBody, 23 + cursor, 1)));
                phg.setGain(Integer.parseInt(new String(msgBody, 24 + cursor, 1)));
                phg.setDirectivity(Integer.parseInt(new String(msgBody, 25 + cursor, 1)));
                de = phg;
            } catch (NumberFormatException nfe) {
                de = null;
            }
        } else if ((char) msgBody[22 + cursor] == '/' && (char) msgBody[18 + cursor] != '_') {
            CourseAndSpeedExtension cse = new CourseAndSpeedExtension();

            String courseString = new String(msgBody, cursor + 19, 3);
            String speedString = new String(msgBody, cursor + 23, 3);
            int course = 0;
            int speed = 0;
            try
            {
                course = Integer.parseInt(courseString);
                speed = Integer.parseInt(speedString);
            } catch (NumberFormatException nfe) {
                course = 0;
                speed = 0;
            }
            cse.setCourse(course);
            cse.setSpeed(speed);
            de = cse;
        }
        return de;
    }

    public static Position parseMICe(byte[] msgBody, final String destinationCall) throws Exception
    {
        String dcall = destinationCall;
        if (destinationCall.indexOf("-") > -1) 
        {
            dcall = destinationCall.substring(0, destinationCall.indexOf("-"));
        }
        if (dcall.length() != 6)
        {
            throw new UnparsablePositionException("MicE Destination Call incorrect length:  " + dcall);
        }

        char[] destcall = dcall.toCharArray();
        for (int i = 0; i < 3; ++i)
        {
            char c = destcall[i + 1];
            if (!(('0' <= c && c <= '9') || ('A' <= c && c <= 'L') || ('P' <= c && c <= 'Z'))) {
                throw new UnparsablePositionException("Digit " + i + " dorked:  " + c);
            }
        }
        for (int i = 3; i < 5; ++i) {
            char c = destcall[i + 1];
            if (!(('0' <= c && c <= '9') || ('L' == c) || ('P' <= c && c <= 'Z'))) {
                throw new UnparsablePositionException("Digit " + i + " dorked:  " + c);
            }
        }
        char c = (char) msgBody[1 + 0];
        if (c < '\u0026' || c > '\u007f') {
            throw new UnparsablePositionException("Raw packet contains " + c + " at position " + 1);
        }
        c = (char) msgBody[1 + 1];
        if (c < '\u0026' || c > '\u0061') {
            throw new UnparsablePositionException("Raw packet contains " + c + " at position " + 2);
        }
        c = (char) msgBody[1 + 2];
        if (c < '\u001c' || c > '\u007f') {
            throw new UnparsablePositionException("Raw packet contains " + c + " at position " + 3);
        }
        c = (char) msgBody[1 + 3];
        if (c < '\u001c' || c > '\u007f') {
            throw new UnparsablePositionException("Raw packet contains " + c + " at position " + 4);
        }
        c = (char) msgBody[1 + 4];
        if (c < '\u001c' || c > '\u007d') {
            throw new UnparsablePositionException("Raw packet contains " + c + " at position " + 5);
        }
        c = (char) msgBody[1 + 5];
        if (c < '\u001c' || c > '\u007f') {
            throw new UnparsablePositionException("Raw packet contains " + c + " at position " + 6);
        }
        c = (char) msgBody[1 + 6];
        if ((c < '\u0021' || c > '\u007b') && (c != '\u007d')) {
            throw new UnparsablePositionException("Raw packet contains " + c + " at position " + 7);
        }
        if (!validSymTableUncompressed((char) msgBody[1 + 7])) {
            throw new UnparsablePositionException("Raw packet contains " + c + " at position " + 8);
        }
        char[] destcall2 = new char[6];
        for (int i = 0; i < 6; ++i) {
            c = destcall[i];
            if ('A' <= c && c <= 'J') {
                destcall2[i] = (char) (c - ('A' - '0'));
            } else if ('P' <= c && c <= 'Y') {
                destcall2[i] = (char) (c - ('P' - '0'));
            } else if ('K' == c || 'L' == c || 'Z' == c) {
                destcall2[i] = '_';
            } else
                destcall2[i] = c;
        }
        int posAmbiguity = 0;
        if (destcall2[5] == '_') {
            destcall2[5] = '5';
            posAmbiguity = 4;
        }
        if (destcall2[4] == '_') {
            destcall2[4] = '5';
            posAmbiguity = 3;
        }
        if (destcall2[3] == '_') {
            destcall2[3] = '5';
            posAmbiguity = 2;
        }
        if (destcall2[2] == '_') {
            destcall2[2] = '3';
            posAmbiguity = 1;
        }
        if (destcall2[1] == '_' || destcall2[0] == '_') {
            throw new UnparsablePositionException("bad pos-ambiguity on destcall");
        }

        double lat = 0.0F;
        try 
        {
            lat = parseDegMin(destcall2, 0, 2, 9, false);
        } catch (Exception e) {
            throw new UnparsablePositionException("Destination Call invalid for MicE:  " + new String(destcall2));
        }
        if (destinationCall.charAt(3) <= 'L') {
            lat = 0.0F - lat;
        }

        int longDeg = (char) msgBody[1 + 0] - 28;
        if ((char) destcall[4] >= 'P')
            longDeg += 100;
        if (longDeg >= 180 && longDeg <= 189)
            longDeg -= 80;
        else if (longDeg >= 190 && longDeg <= 199)
            longDeg -= 190;
        int longMin = (char) msgBody[1 + 1] - 28;
        if (longMin >= 60)
            longMin -= 60;
        int longMinFract = (char) msgBody[1 + 2] - 28;

        float lng = 0.0F;

        switch (posAmbiguity)
        {
            case 0:
                lng = ((float) longDeg + ((float) longMin) / 60.0F + ((float) longMinFract / 6000.0F));
                break;
            case 1:
                lng = ((float) longDeg + ((float) longMin) / 60.0F + ((float) (longMinFract - longMinFract % 10 + 5) / 6000.0F));
                break;
            case 2:
                lng = ((float) longDeg + ((float) longMin) / 60.0F);
                break;
            case 3:
                lng = ((float) longDeg + ((float) (longMin - longMin % 10 + 5)) / 60.0F);
                break;
            case 4:
                lng = ((float) longDeg + 0.5F);
                break;
            default:
                throw new UnparsablePositionException("Unable to extract longitude from MicE");
        }
        if ((char) destcall[1 + 4] >= 'P') { 
            lng = 0.0F - lng;
        }
        return new Position((double) lat, (double) lng, posAmbiguity, (char) msgBody[1 + 7], (char) msgBody[1 + 6]);
    }

    public static CourseAndSpeedExtension parseMICeExtension(byte msgBody[], String destinationField) throws Exception
    {
        CourseAndSpeedExtension cse = new CourseAndSpeedExtension();
        int sp = msgBody[1 + 3] - 28;
        int dc = msgBody[1 + 4] - 28;
        int se = msgBody[1 + 5] - 28;
        int speed = sp * 10;
        int q = (int) (dc / 10);
        speed += q;
        int r = (int) (dc % 10) * 100;
        int course = r + se;
        if (course >= 400)
            course -= 400;
        if (speed >= 800)
            speed -= 800;
        cse.setSpeed(speed);
        cse.setCourse(course);
        return cse;
    }

    public static Position parseNMEA(byte[] msgBody) throws Exception
    {
        String[] nmea = commaSplit.split(new String(msgBody));
        String lats = null;
        String lngs = null;
        String lath = null;
        String lngh = null;
        if (nmea.length < 5)
        {
            throw new UnparsablePositionException("Too few parts in NMEA sentence");
        }

        if ("$GPGGA".equals(nmea[0]) && nmea.length >= 15)
        {
            if (!("1".equals(nmea[6])))
            {
                throw new UnparsablePositionException("Not a valid position fix");
            }

            lats = nmea[2];
            lath = nmea[3];
            lngs = nmea[4];
            lngh = nmea[5];

        } else if ("$GPGLL".equals(nmea[0]) && nmea.length > 7) {
            if (!"A".equals(nmea[6]) || nmea[7].charAt(0) != 'A')
            {
                throw new UnparsablePositionException("Not valid or not autonomous NMEA sentence");
            }

            lats = nmea[1];
            lath = nmea[2];
            lngs = nmea[3];
            lngh = nmea[4];

        } else if ("$GPRMC".equals(nmea[0]) && nmea.length > 11) {
            if (!nmea[2].equals("A")) {
                throw new UnparsablePositionException("Not valid or not autonomous NMEA sentence");
            }
            lats = nmea[3];
            lath = nmea[4];
            lngs = nmea[5];
            lngh = nmea[6];
        } else if ("$GPWPL".equals(nmea[0]) && nmea.length > 5) {
            lats = nmea[1];
            lath = nmea[2];
            lngs = nmea[3];
            lngh = nmea[4];
        } else if (nmea.length > 15 && "$PNTS".equals(nmea[0]) && "1".equals(nmea[1])) {
            lats = nmea[7];
            lath = nmea[8];
            lngs = nmea[9];
            lngh = nmea[10];
        } else if ("$GPGSA".equals(nmea[0]) || "$GPVTG".equals(nmea[0]) || "$GPGSV".equals(nmea[0])) {
            throw new UnparsablePositionException("Ignored NMEA sentence");
        }

        if (lats == null) {
            throw new UnparsablePositionException("Invalid NMEA sentence");
        }
        try {
            double lat = parseDegMin(lats.toCharArray(), 0, 2, 9, true);
            double lng = parseDegMin(lngs.toCharArray(), 0, 3, 9, true);
            if (lat > 90.0F)
                throw new UnparsablePositionException("Latitude too high");
            if (lng > 180.0F)
                throw new UnparsablePositionException("Longitude too high");

            if (lath.equals("S") || lath.equals("s"))
                lat = 0.0F - lat;
            else if (!(lath.equals("N") || lath.equals("n")))
                throw new UnparsablePositionException("Bad latitude sign");

            if (lngh.equals("W") || lngh.equals("w"))
                lng = 0.0F - lng;
            else if (!(lngh.equals("E") || lngh.equals("e")))
                throw new UnparsablePositionException("Bad longitude sign");
            return new Position(lat, lng, 0, '/', '>');
        } catch (Exception e) {
            throw new UnparsablePositionException("Abject failure parsing NMEA sentence");
        }
    }

    public static Position parseCompressed(byte[] msgBody, int cursor) throws Exception
    {
        if (msgBody.length < cursor + 13)
        {
            throw new UnparsablePositionException("Compressed position too short");
        }

        for (int i = 1; i < 9; ++i)
        {
            char c = (char) msgBody[cursor + i];
            if (c < 0x21 || c > 0x7b) {
                throw new UnparsablePositionException("Compressed position characters out of range");
            }
        }

        int lat1 = (char) msgBody[cursor + 1] - 33;
        int lat2 = (char) msgBody[cursor + 2] - 33;
        int lat3 = (char) msgBody[cursor + 3] - 33;
        int lat4 = (char) msgBody[cursor + 4] - 33;

        int lng1 = (char) msgBody[cursor + 5] - 33;
        int lng2 = (char) msgBody[cursor + 6] - 33;
        int lng3 = (char) msgBody[cursor + 7] - 33;
        int lng4 = (char) msgBody[cursor + 8] - 33;

        float lat = 90.0F - ((float) (lat1 * 91 * 91 * 91 + lat2 * 91 * 91 + lat3 * 91 + lat4) / 380926.0F);
        float lng = -180.0F + ((float) (lng1 * 91 * 91 * 91 + lng2 * 91 * 91 + lng3 * 91 + lng4) / 190463.0F);
        return new Position(lat, lng, 0, (char) msgBody[cursor + 0], (char) msgBody[cursor + 9]);
    }

    public static DataExtension parseCompressedExtension(byte[] msgBody, int cursor) throws Exception
    {
        DataExtension de = null;
        if (msgBody[cursor + 9] == '_') {
            return null;
        }
        int t = (char) msgBody[cursor + 12] - 33;
        int nmeaSource = (t & 0x18) >> 3;
        if (nmeaSource == 2) {
            return null;
        }
        int c = (char) msgBody[cursor + 10] - 33;
        if (c + 33 == ' ') {
            return null;
        }
        if (c < 90) {
            int s = (char) msgBody[cursor + 11] - 33;
            CourseAndSpeedExtension cse = new CourseAndSpeedExtension();
            cse.setCourse(c * 4);
            cse.setSpeed((int) Math.round(Math.pow(1.08, s) - 1));
            de = cse;
        } else if (c == (char) ('{')) {
            int s = (char) msgBody[cursor + 11] - 33;
            s = (int) Math.round(2 * Math.pow(1.08, s));
            RangeExtension re = new RangeExtension(s);
            de = re;
        }
        return de;
    }

    private static double parseDegMin(char[] txt, int cursor, int degSize, int len, boolean decimalDot) throws Exception 
    {
        if (txt == null || txt.length < cursor + degSize + 2)
            throw new Exception("Too short degmin data");
        double result = 0.0F;
        for (int i = 0; i < degSize; ++i) {
            char c = txt[cursor + i];
            if (c < '0' || c > '9')
                throw new Exception("Bad input decimals:  " + c);
            result = result * 10.0F + (c - '0');
        }
        double minFactor = 10.0F;
        double minutes = 0.0F;
        int mLen = txt.length - degSize - cursor;
        if (mLen > len - degSize)
            mLen = len - degSize;
        for (int i = 0; i < mLen; ++i)
        {
            char c = txt[cursor + degSize + i];
            if (decimalDot && i == 2) {
                if (c == '.')
                    continue;
                throw new Exception("Expected decimal dot");
            }
            if (c < '0' || c > '9')
                throw new Exception("Bad input decimals: " + c);
            minutes += minFactor * (c - '0');
            minFactor *= 0.1D;
        }
        if (minutes >= 60.0D)
            throw new Exception("Bad minutes value - 60.0 or over");
        result += minutes / 60.0D;
        result = Math.round(result * 100000.0) * 0.00001D;
        if (degSize == 2 && result > 90.01D)
            throw new Exception("Latitude value too high");
        if (degSize == 3 && result > 180.01F)
            throw new Exception("Longitude value too high");
        return result;
    }

    private static boolean validSymTableUncompressed(char c)
    {
        if (c == '/' || c == '\\')
            return true;
        if ('A' <= c && c <= 'Z')
            return true;
        if ('0' <= c && c <= '9')
            return true;
        return false;
    }
}
