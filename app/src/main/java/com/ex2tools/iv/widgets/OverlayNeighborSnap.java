package com.ex2tools.iv.widgets;

import android.graphics.Rect;

import java.util.List;

final class OverlayNeighborSnap {
    private OverlayNeighborSnap() {}

    static Rect find(Rect drop, List<Rect> neighbors, int screenWidth, int screenHeight,
                     int gap, int reach) {
        int width = drop.width();
        int height = drop.height();
        int maxX = Math.max(0, screenWidth - width);
        int maxY = Math.max(0, screenHeight - height);
        int x = Math.max(0, Math.min(maxX, drop.left));
        int y = Math.max(0, Math.min(maxY, drop.top));
        Rect best = new Rect(x, y, x + width, y + height);
        long bestDistance = (long) reach * reach + 1;

        for (Rect neighbor : neighbors) {
            int left = neighbor.left - gap - width;
            int right = neighbor.right + gap;
            int above = neighbor.top - gap - height;
            int below = neighbor.bottom + gap;
            int alignRight = neighbor.right - width;
            int alignBottom = neighbor.bottom - height;
            int[][] anchors = {
                    {left, neighbor.top}, {left, alignBottom},
                    {right, neighbor.top}, {right, alignBottom},
                    {neighbor.left, above}, {alignRight, above},
                    {neighbor.left, below}, {alignRight, below}
            };
            for (int[] anchor : anchors) {
                int candidateX = anchor[0];
                int candidateY = anchor[1];
                if (candidateX < 0 || candidateX > maxX || candidateY < 0 || candidateY > maxY) continue;
                long dx = (long) candidateX - x;
                long dy = (long) candidateY - y;
                long distance = dx * dx + dy * dy;
                if (distance >= bestDistance) continue;
                Rect candidate = new Rect(candidateX, candidateY, candidateX + width, candidateY + height);
                boolean overlaps = false;
                for (Rect other : neighbors) {
                    if (Rect.intersects(candidate, other)) {
                        overlaps = true;
                        break;
                    }
                }
                if (!overlaps) {
                    best = candidate;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }
}
