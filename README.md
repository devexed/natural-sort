Java comparator class for comparing text strings in a locale dependent, "natural" rather than alphabetical way.

```java
List<String> humbugs = Arrays.asList(
  "humbug 1",
  "humbug 2",
  "humbug 11",
  "humbug 12");
  
// Default, alphabetical, sorting of strings. Yields:
// humbug 1
// humbug 11
// humbug 12
// humbug 2
Collections.sort(humbugs);

// Natural order sorting of strings (for the default locale). Yields:
// humbug 1
// humbug 2
// humbug 11
// humbug 12
Collections.sort(humbugs, new NaturalOrderComparator());
```
