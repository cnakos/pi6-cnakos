import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class RandomUtils {

  private static Random rand;

  static {
    rand = new Random(123456789); // Use the same seed intentionally
  }

  public static <T> List<T> getRandomSubset(List<T> org, int num) {
    int size = org.size();
    if (num >= size)
      return org;

    List<T> subset = new ArrayList<T>();
    Set<Integer> ints = new HashSet<Integer>();
    while (subset.size() < num) {
      int randInt = rand.nextInt(size);
      if (!ints.contains(randInt)) {
        ints.add(randInt);
        subset.add(org.get(randInt));
      }
    }
    return subset;
  }
}
