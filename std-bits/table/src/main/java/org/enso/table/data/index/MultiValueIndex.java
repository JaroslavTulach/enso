package org.enso.table.data.index;

import org.enso.table.data.column.builder.object.*;
import org.enso.table.data.column.builder.object.StringBuilder;
import org.enso.table.data.column.storage.Storage;
import org.enso.table.data.table.Column;
import org.enso.table.data.table.Table;
import org.enso.table.data.table.aggregate.AggregateColumnDefinition;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

public class MultiValueIndex {
  private final Table parent;
  private final Storage[] keyColumns;
  private final Map<MultiValueKey, List<Integer>> locs;

  public MultiValueIndex(Table parent, Storage[] keyColumns) {
    this.parent = parent;
    this.keyColumns = keyColumns;

    this.locs = new HashMap<>();
    if (keyColumns.length != 0) {
      int size = keyColumns[0].size();
      for (int i = 0; i < size; i++) {
        int finalI = i;
        MultiValueKey key = new MultiValueKey(Arrays.stream(keyColumns).map(c -> c.getItemBoxed(finalI)).toArray());
        List<Integer> its = this.locs.computeIfAbsent(key, x -> new ArrayList<>());
        its.add(i);
      }
    }
  }

  public Table makeTable(AggregateColumnDefinition[] columns) {
    final int length = columns.length;
    final int size = locs.size();

    Builder[] storage = Arrays.stream(columns).map(c -> getBuilderForType(c.getType(), size)).toArray(Builder[]::new);

    for (List<Integer> group_locs: this.locs.values()) {
      for (int i = 0; i < length; i++) {
        AggregateColumnDefinition column = columns[i];

        Object reduced = column.getInitialValue();
        for (int idx: group_locs) {
          reduced = column.aggregate(reduced, this.parent, idx);
        }

        storage[i].appendNoGrow(column.finalise(reduced));
      }
    }

    return new Table(
        IntStream.range(0, length)
            .mapToObj(i -> new Column(columns[i].getName(), storage[i].seal()))
            .toArray(Column[]::new));
  }

  private static Builder getBuilderForType(int type, int size) {
    switch (type) {
      case Storage.Type.BOOL: return new BoolBuilder();
      case Storage.Type.DOUBLE: return NumericBuilder.createDoubleBuilder(size);
      case Storage.Type.LONG: return NumericBuilder.createLongBuilder(size);
      case Storage.Type.STRING: return new StringBuilder(size);
      case Storage.Type.OBJECT: return new ObjectBuilder(size);
    }
    return new InferredBuilder(size);
  }
}
