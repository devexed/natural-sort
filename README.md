Java comparator class for comparing text strings in a locale dependent, "natural" rather than alphabetical way.

```java
List<String> humbugs = Arrays.asList(
  "humbug 1",
  "humbug 2",
  "humbug 11",
  "humbug 12");
  
// Default, alphabetical, sorting of strings.
Collections.sort(humbugs);
// Yields:
// humbug 1
// humbug 11
// humbug 12
// humbug 2

// Natural order sorting of strings (for the default locale).
Collections.sort(humbugs, new NaturalOrderComparator<String>());
// Yields:
// humbug 1
// humbug 2
// humbug 11
// humbug 12
```
