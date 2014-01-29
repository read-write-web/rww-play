package utils;

import java.util.*;


// TODO create a separe project or publish this on maven central or something

/*
        ##############################################################################################################
        ##############################################################################################################
        ##############################################################################################################
        ##############################################################################################################
        Copied Java class from http://code.google.com/p/mimeparse
        Adapted for current LDP project: removed all dependencies
        ##############################################################################################################
        ##############################################################################################################
        ##############################################################################################################
        ##############################################################################################################

        Exemple usage:
        List<String> mimeTypesSupported = Arrays.asList(StringUtils.split(
                "application/xbel+xml,text/xml", ','));
        String bestMatch = MIMEParse.bestMatch(mimeTypesSupported, mimeTypeHeader);

*/

/**
 * MIME-Type Parser
 *
 * This class provides basic functions for handling mime-types. It can handle
 * matching mime-types against a list of media-ranges. See section 14.1 of the
 * HTTP specification [RFC 2616] for a complete explanation.
 *
 * http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.1
 *
 * A port to Java of Joe Gregorio's MIME-Type Parser:
 *
 * http://code.google.com/p/mimeparse/
 *
 * Ported by Tom Zellman <tzellman@gmail.com>.
 *
 */
public final class MIMEParse
{

    /**
     * Parse results container
     */
    protected static class ParseResults
    {
        String type;

        String subType;

        // !a dictionary of all the parameters for the media range
        Map<String, String> params;

        @Override
        public String toString()
        {
            StringBuffer s = new StringBuffer("('" + type + "', '" + subType
                    + "', {");
            for (String k : params.keySet())
                s.append("'" + k + "':'" + params.get(k) + "',");
            return s.append("})").toString();
        }
    }

    /**
     * Carves up a mime-type and returns a ParseResults object
     *
     * For example, the media range 'application/xhtml;q=0.5' would get parsed
     * into:
     *
     * ('application', 'xhtml', {'q', '0.5'})
     */
    protected static ParseResults parseMimeType(String mimeType)
    {
        String[] parts = split(mimeType, ";");
        ParseResults results = new ParseResults();
        results.params = new HashMap<String, String>();

        for (int i = 1; i < parts.length; ++i)
        {
            String p = parts[i];
            String[] subParts = split(p, '=');
            if (subParts.length == 2)
                results.params.put(subParts[0].trim(), subParts[1].trim());
        }
        String fullType = parts[0].trim();

        // Java URLConnection class sends an Accept header that includes a
        // single "*" - Turn it into a legal wildcard.
        if (fullType.equals("*"))
            fullType = "*/*";
        String[] types = split(fullType, "/");
        results.type = types[0].trim();
        results.subType = types[1].trim();
        return results;
    }

    /**
     * Carves up a media range and returns a ParseResults.
     *
     * For example, the media range 'application/*;q=0.5' would get parsed into:
     *
     * ('application', '*', {'q', '0.5'})
     *
     * In addition this function also guarantees that there is a value for 'q'
     * in the params dictionary, filling it in with a proper default if
     * necessary.
     *
     * @param range
     */
    protected static ParseResults parseMediaRange(String range)
    {
        ParseResults results = parseMimeType(range);
        String q = results.params.get("q");
        float f = toFloat(q, 1);
        if (isBlank(q) || f < 0 || f > 1)
            results.params.put("q", "1");
        return results;
    }

    /**
     * Structure for holding a fitness/quality combo
     */
    protected static class FitnessAndQuality implements
            Comparable<FitnessAndQuality>
    {
        int fitness;

        float quality;

        String mimeType; // optionally used

        public FitnessAndQuality(int fitness, float quality)
        {
            this.fitness = fitness;
            this.quality = quality;
        }

        public int compareTo(FitnessAndQuality o)
        {
            if (fitness == o.fitness)
            {
                if (quality == o.quality)
                    return 0;
                else
                    return quality < o.quality ? -1 : 1;
            }
            else
                return fitness < o.fitness ? -1 : 1;
        }
    }

    /**
     * Find the best match for a given mimeType against a list of media_ranges
     * that have already been parsed by MimeParse.parseMediaRange(). Returns a
     * tuple of the fitness value and the value of the 'q' quality parameter of
     * the best match, or (-1, 0) if no match was found. Just as for
     * quality_parsed(), 'parsed_ranges' must be a list of parsed media ranges.
     *
     * @param mimeType
     * @param parsedRanges
     */
    protected static FitnessAndQuality fitnessAndQualityParsed(String mimeType,
                                                               Collection<ParseResults> parsedRanges)
    {
        int bestFitness = -1;
        float bestFitQ = 0;
        ParseResults target = parseMediaRange(mimeType);

        for (ParseResults range : parsedRanges)
        {
            if ((target.type.equals(range.type) || range.type.equals("*") || target.type
                    .equals("*"))
                    && (target.subType.equals(range.subType)
                    || range.subType.equals("*") || target.subType
                    .equals("*")))
            {
                for (String k : target.params.keySet())
                {
                    int paramMatches = 0;
                    if (!k.equals("q") && range.params.containsKey(k)
                            && target.params.get(k).equals(range.params.get(k)))
                    {
                        paramMatches++;
                    }
                    int fitness = (range.type.equals(target.type)) ? 100 : 0;
                    fitness += (range.subType.equals(target.subType)) ? 10 : 0;
                    fitness += paramMatches;
                    if (fitness > bestFitness)
                    {
                        bestFitness = fitness;
                        bestFitQ = toFloat(range.params.get("q"), 0);
                    }
                }
            }
        }
        return new FitnessAndQuality(bestFitness, bestFitQ);
    }

    /**
     * Find the best match for a given mime-type against a list of ranges that
     * have already been parsed by parseMediaRange(). Returns the 'q' quality
     * parameter of the best match, 0 if no match was found. This function
     * bahaves the same as quality() except that 'parsed_ranges' must be a list
     * of parsed media ranges.
     *
     * @param mimeType
     * @param parsedRanges
     * @return
     */
    protected static float qualityParsed(String mimeType,
                                         Collection<ParseResults> parsedRanges)
    {
        return fitnessAndQualityParsed(mimeType, parsedRanges).quality;
    }

    /**
     * Returns the quality 'q' of a mime-type when compared against the
     * mediaRanges in ranges. For example:
     *
     * @param mimeType
     * @param ranges
     */
    public static float quality(String mimeType, String ranges)
    {
        List<ParseResults> results = new LinkedList<ParseResults>();
        for (String r : split(ranges, ','))
            results.add(parseMediaRange(r));
        return qualityParsed(mimeType, results);
    }

    /**
     * Takes a list of supported mime-types and finds the best match for all the
     * media-ranges listed in header. The value of header must be a string that
     * conforms to the format of the HTTP Accept: header. The value of
     * 'supported' is a list of mime-types.
     *
     * MimeParse.bestMatch(Arrays.asList(new String[]{"application/xbel+xml",
     * "text/xml"}), "text/*;q=0.5,*; q=0.1") 'text/xml'
     *
     * @param supported
     * @param header
     * @return
     */
    public static String bestMatch(Collection<String> supported, String header)
    {
        List<ParseResults> parseResults = new LinkedList<ParseResults>();
        List<FitnessAndQuality> weightedMatches = new LinkedList<FitnessAndQuality>();
        for (String r : split(header, ','))
            parseResults.add(parseMediaRange(r));

        for (String s : supported)
        {
            FitnessAndQuality fitnessAndQuality = fitnessAndQualityParsed(s,
                    parseResults);
            fitnessAndQuality.mimeType = s;
            weightedMatches.add(fitnessAndQuality);
        }
        Collections.sort(weightedMatches);

        FitnessAndQuality lastOne = weightedMatches
                .get(weightedMatches.size() - 1);
        return compare(lastOne.quality, 0) != 0 ? lastOne.mimeType
                : "";
    }

    // hidden
    private MIMEParse()
    {
    }






    /*
        ##############################################################################################################
        ##############################################################################################################
        ##############################################################################################################
        ##############################################################################################################
        Copied and adapted from Apache Commons to remove the dependency
        ##############################################################################################################
        ##############################################################################################################
        ##############################################################################################################
        ##############################################################################################################
     */

    /**
     * <p>Compares two floats for order.</p>
     *
     * <p>This method is more comprehensive than the standard Java greater than,
     * less than and equals operators.</p>
     * <ul>
     *  <li>It returns <code>-1</code> if the first value is less than the second.
     *  <li>It returns <code>+1</code> if the first value is greater than the second.
     *  <li>It returns <code>0</code> if the values are equal.
     * </ul>
     *
     * <p> The ordering is as follows, largest to smallest:
     * <ul>
     * <li>NaN
     * <li>Positive infinity
     * <li>Maximum float
     * <li>Normal positive numbers
     * <li>+0.0
     * <li>-0.0
     * <li>Normal negative numbers
     * <li>Minimum float (<code>-Float.MAX_VALUE</code>)
     * <li>Negative infinity
     * </ul>
     *
     * <p>Comparing <code>NaN</code> with <code>NaN</code> will return
     * <code>0</code>.</p>
     *
     * @param lhs  the first <code>float</code>
     * @param rhs  the second <code>float</code>
     * @return <code>-1</code> if lhs is less, <code>+1</code> if greater,
     *  <code>0</code> if equal to rhs
     */
    private static int compare(float lhs, float rhs) {
        if (lhs < rhs) {
            return -1;
        }
        if (lhs > rhs) {
            return +1;
        }
        //Need to compare bits to handle 0.0 == -0.0 being true
        // compare should put -0.0 < +0.0
        // Two NaNs are also == for compare purposes
        // where NaN == NaN is false
        int lhsBits = Float.floatToIntBits(lhs);
        int rhsBits = Float.floatToIntBits(rhs);
        if (lhsBits == rhsBits) {
            return 0;
        }
        //Something exotic! A comparison to NaN or 0.0 vs -0.0
        //Fortunately NaN's int is > than everything else
        //Also negzeros bits < poszero
        //NAN: 2143289344
        //MAX: 2139095039
        //NEGZERO: -2147483648
        if (lhsBits < rhsBits) {
            return -1;
        } else {
            return +1;
        }
    }

    /**
     * <p>Convert a <code>String</code> to a <code>float</code>, returning a
     * default value if the conversion fails.</p>
     *
     * <p>If the string <code>str</code> is <code>null</code>, the default
     * value is returned.</p>
     *
     * <pre>
     *   NumberUtils.toFloat(null, 1.1f)   = 1.0f
     *   NumberUtils.toFloat("", 1.1f)     = 1.1f
     *   NumberUtils.toFloat("1.5", 0.0f)  = 1.5f
     * </pre>
     *
     * @param str the string to convert, may be <code>null</code>
     * @param defaultValue the default value
     * @return the float represented by the string, or defaultValue
     *  if conversion fails
     * @since 2.1
     */
    private static float toFloat(String str, float defaultValue) {
        if (str == null) {
            return defaultValue;
        }
        try {
            return Float.parseFloat(str);
        } catch (NumberFormatException nfe) {
            return defaultValue;
        }
    }

    /**
     * Performs the logic for the <code>split</code> and
     * <code>splitPreserveAllTokens</code> methods that do not return a
     * maximum array length.
     *
     * @param str  the String to parse, may be <code>null</code>
     * @param separatorChar the separate character
     * @param preserveAllTokens if <code>true</code>, adjacent separators are
     * treated as empty token separators; if <code>false</code>, adjacent
     * separators are treated as one separator.
     * @return an array of parsed Strings, <code>null</code> if null String input
     */
    private static String[] splitWorker(String str, char separatorChar, boolean preserveAllTokens) {
        // Performance tuned for 2.0 (JDK1.4)

        if (str == null) {
            return null;
        }
        int len = str.length();
        if (len == 0) {
            return EMPTY_STRING_ARRAY;
        }
        List list = new ArrayList();
        int i = 0, start = 0;
        boolean match = false;
        boolean lastMatch = false;
        while (i < len) {
            if (str.charAt(i) == separatorChar) {
                if (match || preserveAllTokens) {
                    list.add(str.substring(start, i));
                    match = false;
                    lastMatch = true;
                }
                start = ++i;
                continue;
            } else {
                lastMatch = false;
            }
            match = true;
            i++;
        }
        if (match || (preserveAllTokens && lastMatch)) {
            list.add(str.substring(start, i));
        }
        return (String[]) list.toArray(new String[list.size()]);
    }

    /**
     * Performs the logic for the <code>split</code> and
     * <code>splitPreserveAllTokens</code> methods that return a maximum array
     * length.
     *
     * @param str  the String to parse, may be <code>null</code>
     * @param separatorChars the separate character
     * @param max  the maximum number of elements to include in the
     *  array. A zero or negative value implies no limit.
     * @param preserveAllTokens if <code>true</code>, adjacent separators are
     * treated as empty token separators; if <code>false</code>, adjacent
     * separators are treated as one separator.
     * @return an array of parsed Strings, <code>null</code> if null String input
     */
    private static String[] splitWorker(String str, String separatorChars, int max, boolean preserveAllTokens) {
        // Performance tuned for 2.0 (JDK1.4)
        // Direct code is quicker than StringTokenizer.
        // Also, StringTokenizer uses isSpace() not isWhitespace()

        if (str == null) {
            return null;
        }
        int len = str.length();
        if (len == 0) {
            return EMPTY_STRING_ARRAY;
        }
        List list = new ArrayList();
        int sizePlus1 = 1;
        int i = 0, start = 0;
        boolean match = false;
        boolean lastMatch = false;
        if (separatorChars == null) {
            // Null separator means use whitespace
            while (i < len) {
                if (Character.isWhitespace(str.charAt(i))) {
                    if (match || preserveAllTokens) {
                        lastMatch = true;
                        if (sizePlus1++ == max) {
                            i = len;
                            lastMatch = false;
                        }
                        list.add(str.substring(start, i));
                        match = false;
                    }
                    start = ++i;
                    continue;
                } else {
                    lastMatch = false;
                }
                match = true;
                i++;
            }
        } else if (separatorChars.length() == 1) {
            // Optimise 1 character case
            char sep = separatorChars.charAt(0);
            while (i < len) {
                if (str.charAt(i) == sep) {
                    if (match || preserveAllTokens) {
                        lastMatch = true;
                        if (sizePlus1++ == max) {
                            i = len;
                            lastMatch = false;
                        }
                        list.add(str.substring(start, i));
                        match = false;
                    }
                    start = ++i;
                    continue;
                } else {
                    lastMatch = false;
                }
                match = true;
                i++;
            }
        } else {
            // standard case
            while (i < len) {
                if (separatorChars.indexOf(str.charAt(i)) >= 0) {
                    if (match || preserveAllTokens) {
                        lastMatch = true;
                        if (sizePlus1++ == max) {
                            i = len;
                            lastMatch = false;
                        }
                        list.add(str.substring(start, i));
                        match = false;
                    }
                    start = ++i;
                    continue;
                } else {
                    lastMatch = false;
                }
                match = true;
                i++;
            }
        }
        if (match || (preserveAllTokens && lastMatch)) {
            list.add(str.substring(start, i));
        }
        return (String[]) list.toArray(new String[list.size()]);
    }

    /**
     * <p>Splits the provided text into an array, separator specified.
     * This is an alternative to using StringTokenizer.</p>
     *
     * <p>The separator is not included in the returned String array.
     * Adjacent separators are treated as one separator.
     * For more control over the split use the StrTokenizer class.</p>
     *
     * <p>A <code>null</code> input String returns <code>null</code>.</p>
     *
     * <pre>
     * StringUtils.split(null, *)         = null
     * StringUtils.split("", *)           = []
     * StringUtils.split("a.b.c", '.')    = ["a", "b", "c"]
     * StringUtils.split("a..b.c", '.')   = ["a", "b", "c"]
     * StringUtils.split("a:b:c", '.')    = ["a:b:c"]
     * StringUtils.split("a\tb\nc", null) = ["a", "b", "c"]
     * StringUtils.split("a b c", ' ')    = ["a", "b", "c"]
     * </pre>
     *
     * @param str  the String to parse, may be null
     * @param separatorChar  the character used as the delimiter,
     *  <code>null</code> splits on whitespace
     * @return an array of parsed Strings, <code>null</code> if null String input
     * @since 2.0
     */
    private static String[] split(String str, char separatorChar) {
        return splitWorker(str, separatorChar, false);
    }

    /**
     * <p>Splits the provided text into an array, separators specified.
     * This is an alternative to using StringTokenizer.</p>
     *
     * <p>The separator is not included in the returned String array.
     * Adjacent separators are treated as one separator.
     * For more control over the split use the StrTokenizer class.</p>
     *
     * <p>A <code>null</code> input String returns <code>null</code>.
     * A <code>null</code> separatorChars splits on whitespace.</p>
     *
     * <pre>
     * StringUtils.split(null, *)         = null
     * StringUtils.split("", *)           = []
     * StringUtils.split("abc def", null) = ["abc", "def"]
     * StringUtils.split("abc def", " ")  = ["abc", "def"]
     * StringUtils.split("abc  def", " ") = ["abc", "def"]
     * StringUtils.split("ab:cd:ef", ":") = ["ab", "cd", "ef"]
     * </pre>
     *
     * @param str  the String to parse, may be null
     * @param separatorChars  the characters used as the delimiters,
     *  <code>null</code> splits on whitespace
     * @return an array of parsed Strings, <code>null</code> if null String input
     */
    private static String[] split(String str, String separatorChars) {
        return splitWorker(str, separatorChars, -1, false);
    }

    /**
     * <p>Checks if a String is whitespace, empty ("") or null.</p>
     *
     * <pre>
     * StringUtils.isBlank(null)      = true
     * StringUtils.isBlank("")        = true
     * StringUtils.isBlank(" ")       = true
     * StringUtils.isBlank("bob")     = false
     * StringUtils.isBlank("  bob  ") = false
     * </pre>
     *
     * @param str  the String to check, may be null
     * @return <code>true</code> if the String is null, empty or whitespace
     * @since 2.0
     */
    private static boolean isBlank(String str) {
        int strLen;
        if (str == null || (strLen = str.length()) == 0) {
            return true;
        }
        for (int i = 0; i < strLen; i++) {
            if ((Character.isWhitespace(str.charAt(i)) == false)) {
                return false;
            }
        }
        return true;
    }

    private static final String[] EMPTY_STRING_ARRAY = new String[0];
}

