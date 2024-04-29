// java
// List<Integer> doubleList = list.stream()
//         .map(m -> m * 2)
//         .collection(Collectors.toList());

val doubleList = list.map { it * 2 }

// 중괄호 사용 명심!

members
  .filter { it.age == 24 }
  .map { it.name }
  .sorted()

numbers
  .map(Math:: rount)
  .forEach(System.out::println)

numbers
  .map { it.roundToLong() }
  .forEach { println(it) }