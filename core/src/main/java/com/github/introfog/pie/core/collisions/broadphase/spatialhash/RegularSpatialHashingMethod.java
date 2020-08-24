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
package com.github.introfog.pie.core.collisions.broadphase.spatialhash;

import com.github.introfog.pie.core.collisions.broadphase.AbstractBroadPhase;
import com.github.introfog.pie.core.collisions.broadphase.BruteForceMethod;
import com.github.introfog.pie.core.math.MathPIE;
import com.github.introfog.pie.core.math.Vector2f;
import com.github.introfog.pie.core.shape.AABB;
import com.github.introfog.pie.core.shape.IShape;
import com.github.introfog.pie.core.util.ShapePair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The class is a regular spatial hashing method that divides space into cells, which are stored in a hash table.
 * Further, if the shape AABB intersects with a cell, then the reference to this shape is placed in the cell
 * and at the end go through all the cells, and if two shapes are in the same cell, then put them in the
 * list of possibly intersecting shapes.
 *
 * <p>
 * Note, hash table re-filling occurs only when the size of the cells are changed. Also, if the shape AABB
 * has gone beyond the cells to which it belonged in the previous iteration, then the shape will be
 * re-inserted into a hash table.
 *
 * <p>
 * This method is effective for liquids.
 *
 * @see AbstractBroadPhase
 * @see com.github.introfog.pie.core.collisions.broadphase.SpatialHashingMethod
 */
public class RegularSpatialHashingMethod extends AbstractBroadPhase {
    private int cellSize;
    private final Map<Integer, List<IShape>> cells;
    private final Map<IShape, List<Cell>> objects;

    /**
     * Instantiates a new {@link RegularSpatialHashingMethod} instance.
     */
    public RegularSpatialHashingMethod() {
        cellSize = 0;
        cells = new HashMap<>();
        objects = new HashMap<>();
    }

    @Override
    public void addShape(IShape shape) {
        super.addShape(shape);
        insert(shape);
    }

    @Override
    public void setShapes(List<IShape> shapes) {
        super.setShapes(shapes);
        cellSize = calculateCellSize();
        cells.clear();
        objects.clear();
        this.shapes.forEach(this::insert);
    }

    @Override
    public List<ShapePair> domesticCalculateAabbCollisions() {
        List<ShapePair> possibleCollisionList = new ArrayList<>();

        int newCellSize = calculateCellSize();
        if (cellSize != newCellSize) {
            cellSize = newCellSize;
            cells.clear();
            objects.clear();
            shapes.forEach(this::insert);
        } else {
            updateCells();
        }

        Set<ShapePair> possibleIntersect = computePossibleAabbIntersections();
        possibleIntersect.forEach((pair) -> {
            if (AABB.isIntersected(pair.first.aabb, pair.second.aabb)) {
                possibleCollisionList.add(pair);
            }
        });

        return possibleCollisionList;
    }

    private void updateCells() {
        for (IShape shape : shapes) {
            List<Cell> objectCells = objects.get(shape);
            AABB cellAabb = new AABB();
            // Because the first cell corresponds to the bottom left, and the last
            // to the top right, then can easily calculate the AABB of all cells
            cellAabb.min.set(objectCells.get(0).x, objectCells.get(0).y);
            cellAabb.max.set(objectCells.get(objectCells.size() - 1).x, objectCells.get(objectCells.size() - 1).y);
            cellAabb.min.mul(cellSize);
            cellAabb.max.mul(cellSize);
            if (!AABB.isContained(cellAabb, shape.aabb)) {
                reinsertShape(shape);
            }
        }
    }

    private void reinsertShape(IShape shape) {
        List<Cell> objectCells = objects.get(shape);
        for (Cell cell : objectCells) {
            List<IShape> cellShapes = cells.get(cell.hashCode());
            cellShapes.remove(shape);
            if (cellShapes.isEmpty()) {
                cells.remove(cell.hashCode());
            }
        }
        objects.remove(shape);
        insert(shape);
    }

    private int calculateCellSize() {
        float averageMaxBodiesSize = 0;
        for (IShape shape : shapes) {
            averageMaxBodiesSize += Math.max(shape.aabb.max.x - shape.aabb.min.x, shape.aabb.max.y - shape.aabb.min.y);
        }
        averageMaxBodiesSize /= shapes.size();

        return  (averageMaxBodiesSize == 0) ? 1 : ((int) averageMaxBodiesSize * 2);
    }


    private void insert(IShape shape) {
        AABB aabb = shape.aabb;
        int cellX = MathPIE.fastFloor(aabb.max.x / cellSize) - MathPIE.fastFloor(aabb.min.x / cellSize);
        int cellY = MathPIE.fastFloor(aabb.max.y / cellSize) - MathPIE.fastFloor(aabb.min.y / cellSize);
        // Increment the values of cellX and cellY so that the ends of the shape entering the other cells are also processed
        cellX++;
        cellY++;
        // It is necessary to add cells to the array while preserving the order of elements exactly from the minimum
        // edge to the maximum, this is used in order to calculate the AABB of all the cells of the shape, in order
        // to understand, need to re-insert the shape or not (see #updateCells method).
        for (int i = 0; i < cellX; i++) {
            for (int j = 0; j < cellY; j++) {
                Cell cell = new Cell(aabb.min.x + i * cellSize, aabb.min.y + j * cellSize, cellSize);

                int cellHash = cell.hashCode();
                if (!cells.containsKey(cellHash)) {
                    cells.put(cellHash, new ArrayList<>());
                }
                cells.get(cellHash).add(shape);

                if (!objects.containsKey(shape)) {
                    objects.put(shape, new ArrayList<>());
                }
                objects.get(shape).add(cell);
            }
        }
    }

    private Set<ShapePair> computePossibleAabbIntersections() {
        // HashSet is used because requires the uniqueness of pairs,
        // for example, two shapes can intersect in several cells at once
        Set<ShapePair> possibleIntersect = new HashSet<>();
        cells.forEach((cell, list) -> {
            possibleIntersect.addAll(BruteForceMethod.calculateAabbCollisionsWithoutAabbUpdating(list));
        });
        return possibleIntersect;
    }
}