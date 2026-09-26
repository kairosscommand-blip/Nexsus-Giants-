import java.util.Random;

/**
 * Standalone runtime test for NexusGiants v0.4.0's age<->scale interpolation, run outside the
 * stub/compile sandbox with a plain `java` invocation (no Bukkit involved at all). It reimplements
 * GiantsConfig.scaleForAge()/ageForScale() and GiantMobManager.rollAge()/prettyName()/the
 * "<Species> (Age N)" tag format line-for-line from the real source, then exercises them the way
 * an actual game session would: every age from min to max for several (minScale, maxScale) type
 * configs, the round-trip age -> scale -> age, the raw-scale test-spawn path, and the exact name
 * tag text a player would see over a mob's head.
 *
 * Run with: javac AgeScaleFormulaTest.java && java AgeScaleFormulaTest
 */
public class AgeScaleFormulaTest {

    static int failures = 0;
    static int checks = 0;

    public static void main(String[] args) {
        testLinearInterpolationEndpoints();
        testMonotonicAcrossFullRange();
        testAgeForScaleRoundTrip();
        testDegenerateFlatRange();
        testRollAgeStaysInBounds();
        testNameTagFormat();
        testAsymmetricRanges();

        System.out.println();
        System.out.println(checks + " checks run, " + failures + " failed.");
        if (failures > 0) {
            System.out.println("FAIL");
            System.exit(1);
        } else {
            System.out.println("ALL PASS");
        }
    }

    // ---- reimplementation of GiantsConfig, line-for-line from the real source ----

    static double scaleForAge(int minAge, int maxAge, double minScale, double maxScale, int age) {
        int clamped = Math.max(minAge, Math.min(maxAge, age));
        if (maxAge == minAge) {
            return maxScale;
        }
        double fraction = (clamped - minAge) / (double) (maxAge - minAge);
        return minScale + (maxScale - minScale) * fraction;
    }

    static int ageForScale(int minAge, int maxAge, double minScale, double maxScale, double scale) {
        if (maxScale <= minScale) {
            return maxAge;
        }
        double fraction = clamp01((scale - minScale) / (maxScale - minScale));
        return (int) Math.round(minAge + fraction * (maxAge - minAge));
    }

    static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    static String prettyName(String rawEntityTypeName) {
        String lower = rawEntityTypeName.toLowerCase().replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    static String nameTag(String rawEntityTypeName, int age) {
        return prettyName(rawEntityTypeName) + " (Age " + age + ")";
    }

    // ---- checks ----

    static void testLinearInterpolationEndpoints() {
        section("endpoints: minAge -> minScale, maxAge -> maxScale, for several type configs");
        double[][] configs = {
            {1, 100, 0.3, 3.0},   // default config.yml shape
            {1, 100, 0.3, 12.0},  // a type with a big "overrides" max, e.g. zombie
            {1, 100, 0.3, 0.9},   // a type with a small max (still bigger than minScale)
            {0, 50, 0.1, 5.0},
        };
        for (double[] c : configs) {
            int minAge = (int) c[0], maxAge = (int) c[1];
            double minScale = c[2], maxScale = c[3];
            expect("minAge -> minScale (" + java.util.Arrays.toString(c) + ")",
                    scaleForAge(minAge, maxAge, minScale, maxScale, minAge), minScale, 1e-9);
            expect("maxAge -> maxScale (" + java.util.Arrays.toString(c) + ")",
                    scaleForAge(minAge, maxAge, minScale, maxScale, maxAge), maxScale, 1e-9);
        }
    }

    static void testMonotonicAcrossFullRange() {
        section("strictly increasing scale as age increases, across the whole default range (age 1..100)");
        int minAge = 1, maxAge = 100;
        double minScale = 0.3, maxScale = 3.0;
        double previous = -Double.MAX_VALUE;
        boolean allIncreasing = true;
        for (int age = minAge; age <= maxAge; age++) {
            double scale = scaleForAge(minAge, maxAge, minScale, maxScale, age);
            if (scale <= previous) {
                allIncreasing = false;
            }
            previous = scale;
        }
        expect("age 1..100 scale strictly increasing", allIncreasing, true);

        // The user's own framing: age 100 must be the biggest possible, age 1 the smallest.
        double ageOne = scaleForAge(minAge, maxAge, minScale, maxScale, 1);
        double age100 = scaleForAge(minAge, maxAge, minScale, maxScale, 100);
        expect("age 1 == configured minimum scale", ageOne, minScale, 1e-9);
        expect("age 100 == configured maximum scale", age100, maxScale, 1e-9);
        expect("age 100 bigger than age 1", age100 > ageOne, true);

        double age50 = scaleForAge(minAge, maxAge, minScale, maxScale, 50);
        expect("age 50 sits between age 1 and age 100", age50 > ageOne && age50 < age100, true);
    }

    static void testAgeForScaleRoundTrip() {
        section("ageForScale is a faithful inverse of scaleForAge (round trip within +/-1 age)");
        int minAge = 1, maxAge = 100;
        double minScale = 0.3, maxScale = 3.0;
        for (int age = minAge; age <= maxAge; age++) {
            double scale = scaleForAge(minAge, maxAge, minScale, maxScale, age);
            int recovered = ageForScale(minAge, maxAge, minScale, maxScale, scale);
            expect("round-trip age " + age, Math.abs(recovered - age) <= 1, true);
        }
        // Exact endpoints must round-trip exactly, not just within tolerance.
        expect("round-trip minAge exact", ageForScale(minAge, maxAge, minScale, maxScale, minScale), minAge);
        expect("round-trip maxAge exact", ageForScale(minAge, maxAge, minScale, maxScale, maxScale), maxAge);

        // A scale outside the configured range (e.g. someone manually spawns with an extreme
        // /nexusgiants spawnscale) must clamp to the nearest end, never extrapolate past it.
        expect("scale below minScale clamps to minAge",
                ageForScale(minAge, maxAge, minScale, maxScale, 0.05), minAge);
        expect("scale above maxScale clamps to maxAge",
                ageForScale(minAge, maxAge, minScale, maxScale, 50.0), maxAge);
    }

    static void testDegenerateFlatRange() {
        section("degenerate configs (maxAge == minAge, or maxScale <= minScale) don't divide by zero");
        expect("maxAge == minAge returns maxScale flat",
                scaleForAge(10, 10, 0.3, 3.0, 10), 3.0, 1e-9);
        expect("maxAge == minAge ignores out-of-range age input",
                scaleForAge(10, 10, 0.3, 3.0, 999), 3.0, 1e-9);
        expect("maxScale <= minScale (misconfigured override) ageForScale returns maxAge",
                ageForScale(1, 100, 1.0, 1.0, 1.0), 100);
        expect("maxScale < minScale (inverted misconfiguration) still returns maxAge, no crash",
                ageForScale(1, 100, 3.0, 0.3, 1.5), 100);
    }

    static void testRollAgeStaysInBounds() {
        section("rollAge() (uniform over [minAge, maxAge]) always stays in bounds, and covers the range");
        Random random = new Random(42);
        int minAge = 1, maxAge = 100;
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        boolean sawOne = false, sawHundred = false;
        for (int i = 0; i < 200_000; i++) {
            int age = minAge + random.nextInt(maxAge - minAge + 1);
            min = Math.min(min, age);
            max = Math.max(max, age);
            if (age == 1) sawOne = true;
            if (age == 100) sawHundred = true;
        }
        expect("rollAge never below minAge", min >= minAge, true);
        expect("rollAge never above maxAge", max <= maxAge, true);
        expect("200k rolls actually reach age 1 (smallest)", sawOne, true);
        expect("200k rolls actually reach age 100 (biggest)", sawHundred, true);
    }

    static void testNameTagFormat() {
        section("name tag text matches the user's ask: species + age, e.g. \"Zombie (Age 47)\"");
        expect("ZOMBIE age 47", nameTag("ZOMBIE", 47), "Zombie (Age 47)");
        expect("SKELETON age 1 (smallest)", nameTag("SKELETON", 1), "Skeleton (Age 1)");
        expect("CREEPER age 100 (biggest)", nameTag("CREEPER", 100), "Creeper (Age 100)");
        expect("multi-word type DARK_OAK-style underscore handling", nameTag("CAVE_SPIDER", 12), "Cave spider (Age 12)");
    }

    static void testAsymmetricRanges() {
        section("a type whose configured max scale is SMALLER than another type's still spans its own full min->max");
        // e.g. overrides: chicken: 0.9 (still bigger than the flat 0.3 min-scale, just a much
        // smaller giant ceiling than zombie's 3.0) -- age 100 chicken must still be biggest chicken.
        int minAge = 1, maxAge = 100;
        double minScale = 0.3;
        double chickenMax = 0.9;
        double zombieMax = 12.0;
        double chickenAt1 = scaleForAge(minAge, maxAge, minScale, chickenMax, 1);
        double chickenAt100 = scaleForAge(minAge, maxAge, minScale, chickenMax, 100);
        double zombieAt1 = scaleForAge(minAge, maxAge, minScale, zombieMax, 1);
        double zombieAt100 = scaleForAge(minAge, maxAge, minScale, zombieMax, 100);
        expect("chicken age 100 > chicken age 1", chickenAt100 > chickenAt1, true);
        expect("zombie age 100 > zombie age 1", zombieAt100 > zombieAt1, true);
        expect("age-1 chicken and age-1 zombie start at the SAME flat min-scale (by design)",
                Math.abs(chickenAt1 - zombieAt1) < 1e-9, true);
        expect("age-100 zombie is bigger than age-100 chicken (different ceilings)",
                zombieAt100 > chickenAt100, true);
    }

    // ---- tiny assertion helpers ----

    static void section(String title) {
        System.out.println("-- " + title);
    }

    static void expect(String label, double actual, double expected, double tolerance) {
        checks++;
        if (Math.abs(actual - expected) > tolerance) {
            failures++;
            System.out.println("  [FAIL] " + label + ": expected " + expected + " but got " + actual);
        } else {
            System.out.println("  [ok]   " + label);
        }
    }

    static void expect(String label, int actual, int expected) {
        checks++;
        if (actual != expected) {
            failures++;
            System.out.println("  [FAIL] " + label + ": expected " + expected + " but got " + actual);
        } else {
            System.out.println("  [ok]   " + label);
        }
    }

    static void expect(String label, Object actual, Object expected) {
        checks++;
        if (!java.util.Objects.equals(actual, expected)) {
            failures++;
            System.out.println("  [FAIL] " + label + ": expected " + expected + " but got " + actual);
        } else {
            System.out.println("  [ok]   " + label);
        }
    }

    static void expect(String label, boolean actual, boolean expected) {
        checks++;
        if (actual != expected) {
            failures++;
            System.out.println("  [FAIL] " + label + ": expected " + expected + " but got " + actual);
        } else {
            System.out.println("  [ok]   " + label);
        }
    }
}
