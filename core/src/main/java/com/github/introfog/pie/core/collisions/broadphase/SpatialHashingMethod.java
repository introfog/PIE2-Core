/*
    Copyright 2020 Dmitry Chubrick

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
 */
package com.github.introfog.pie.core.collisions.broadphase;

import com.github.introfog.pie.core.math.MathPie;
import com.github.introfog.pie.core.shape.Aabb;
import com.github.introfog.pie.core.shape.IShape;
import com.github.introfog.pie.core.shape.ShapePair;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The class is a spatial hashing method that divides space into cells, which are stored in a hash table.
 * Further, if the shape Aabb intersects with a cell, then the reference to this shape is placed in the cell
 * and at the end go through all the cells, and if two shapes are in the same cell, then put them in the
 * set of possibly intersecting shapes.
 *
 * <p>
 * Note, the calculation of the size of the cells and filling the hash table occurs every iteration a new.
 *
 * <p>
 * This method is effective for liquids.
 *
 * @see AbstractBroadPhase
 */
public class SpatialHashingMethod extends AbstractBroadPhase {
    private int cellSize;
    private final Map<Integer, Set<IShape>> cells;

    /**
     * Instantiates a new {@link SpatialHashingMethod} instance.
     */
    public SpatialHashingMethod() {
        cellSize = 0;
        cells = new HashMap<>();
    }

    @Override
    public SpatialHashingMethod newInstance() {
        SpatialHashingMethod spatialHashingMethod = new SpatialHashingMethod();
        spatialHashingMethod.setShapes(shapes);
        return spatialHashingMethod;
    }

    @Override
    protected Set<ShapePair> domesticCalculateAabbCollisions() {
        // The complexity is O(n), if the minimum and maximum size of the objects are not very different,
        // but if very different, then the complexity tends to O(n^2)
        calculateCellSize();
        cells.clear();
        shapes.forEach(this::insert);

        return computePossibleAabbIntersections();
    }

    private void calculateCellSize() {
        float averageMaxBodiesSize = 0;
        for (IShape shape : shapes) {
            averageMaxBodiesSize += Math.max(shape.getAabb().max.x - shape.getAabb().min.x,
                    shape.getAabb().max.y - shape.getAabb().min.y);
        }
        averageMaxBodiesSize /= shapes.size();

        cellSize = (averageMaxBodiesSize == 0) ? 1 : ((int) averageMaxBodiesSize * 2);
    }

    private int generateKey(float x, float y) {
        return ((MathPie.fastFloor(x / cellSize) * 73856093) ^ (MathPie.fastFloor(y / cellSize) * 19349663));
    }

    private void insert(IShape shape) {
        final Aabb aabb = shape.getAabb();
        int key;
        int cellX = MathPie.fastFloor(aabb.max.x / cellSize) - MathPie.fastFloor(aabb.min.x / cellSize);
        int cellY = MathPie.fastFloor(aabb.max.y / cellSize) - MathPie.fastFloor(aabb.min.y / cellSize);
        // Increment the values of cellX and cellY so that the ends of the shape entering the other cells are also processed
        cellX++;
        cellY++;
        for (int i = 0; i < cellX; i++) {
            for (int j = 0; j < cellY; j++) {
                key = generateKey(aabb.min.x + i * cellSize, aabb.min.y + j * cellSize);

                if (!cells.containsKey(key)) {
                    cells.put(key, new HashSet<>());
                }
                cells.get(key).add(shape);
            }
        }
    }

    private Set<ShapePair> computePossibleAabbIntersections() {
        // HashSet is used because requires the uniqueness of pairs,
        // for example, two shapes can intersect in several cells at once
        Set<ShapePair> possibleIntersect = new HashSet<>();
        cells.forEach((cell, set) ->
                possibleIntersect.addAll(BruteForceMethod.calculateAabbCollisionsWithoutAabbUpdating(set)));
        return possibleIntersect;
    }
}
