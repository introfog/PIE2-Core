package com.github.introfog.pie.core.collisions.broadphase;

import com.github.introfog.pie.core.math.MathPIE;
import com.github.introfog.pie.core.shape.AABB;
import com.github.introfog.pie.core.shape.IShape;
import com.github.introfog.pie.core.util.ShapePair;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class NewSpatialHashingMethod extends AbstractBroadPhase {
    private int cellSize;
    private final Map<Integer, List<IShape>> cells;

    /**
     * Instantiates a new {@link NewSpatialHashingMethod} instance.
     */
    public NewSpatialHashingMethod() {
        cellSize = 0;
        cells = new HashMap<>();
    }

    @Override
    public List<ShapePair> domesticCalculateAabbCollisions() {
        // The complexity is O(n), if the minimum and maximum size of the objects are not very different,
        // but if very different, then the complexity tends to O(n^2)
        List<ShapePair> possibleCollisionList = new ArrayList<>();

        calculateCellSize();
        cells.clear();
        shapes.forEach(this::insert);

        Set<ShapePair> possibleIntersect = computePossibleAabbIntersections();
        possibleIntersect.forEach((pair) -> {
            if (AABB.isIntersected(pair.first.aabb, pair.second.aabb)) {
                possibleCollisionList.add(pair);
            }
        });

        return possibleCollisionList;
    }

    private void calculateCellSize() {
        float averageMaxBodiesSize = 0;
        for (IShape shape : shapes) {
            averageMaxBodiesSize += Math.max(shape.aabb.max.x - shape.aabb.min.x, shape.aabb.max.y - shape.aabb.min.y);
        }
        averageMaxBodiesSize /= shapes.size();

        cellSize = (averageMaxBodiesSize == 0) ? 1 : ((int) averageMaxBodiesSize * 2);
    }

    private int generateKey(float x, float y) {
        return ((MathPIE.fastFloor(x / cellSize) * 73856093) ^ (MathPIE.fastFloor(y / cellSize) * 19349663));
    }

    private void insert(IShape shape) {
        AABB aabb = shape.aabb;
        int key;
        int cellX = MathPIE.fastFloor(aabb.max.x / cellSize) - MathPIE.fastFloor(aabb.min.x / cellSize);
        int cellY = MathPIE.fastFloor(aabb.max.y / cellSize) - MathPIE.fastFloor(aabb.min.y / cellSize);
        // Increment the values ​​of cellX and cellY so that the ends of the shape entering the other cells are also processed
        cellX++;
        cellY++;
        for (int i = 0; i < cellX; i++) {
            for (int j = 0; j < cellY; j++) {
                key = generateKey(aabb.min.x + i * cellSize, aabb.min.y + j * cellSize);

                if (!cells.containsKey(key)) {
                    cells.put(key, new ArrayList<>());
                }
                cells.get(key).add(shape);
            }
        }
    }

    private Set<ShapePair> computePossibleAabbIntersections() {
        // HashSet is used because requires the uniqueness of pairs,
        // for example, two shapes can intersect in several cells at once
        Set<ShapePair> possibleIntersect = new HashSet<>();
        cells.forEach((cell, list) -> {
            for (int i = 0; i < list.size(); i++) {
                for (int j = i + 1; j < list.size(); j++) {
                    possibleIntersect.add(new ShapePair(list.get(i), list.get(j)));
                }
            }
        });
        return possibleIntersect;
    }
}
