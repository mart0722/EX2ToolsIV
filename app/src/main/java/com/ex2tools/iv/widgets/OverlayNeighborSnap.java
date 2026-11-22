package com.ex2tools.iv.widgets;

import android.graphics.Rect;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class OverlayNeighborSnap {
    private OverlayNeighborSnap() {}

    private record Anchor(int x, int y, boolean aligned) {}

    static Rect find(Rect drop, List<Rect> neighbors, int screenWidth, int screenHeight,
                     int gap, int reach, boolean snapAlongEdges, int pitch) {
        int width = drop.width();
        int height = drop.height();
        int maxX = Math.max(0, screenWidth - width);
        int maxY = Math.max(0, screenHeight - height);
        int x = Math.max(0, Math.min(maxX, drop.left));
        int y = Math.max(0, Math.min(maxY, drop.top));
        Rect best = new Rect(x, y, x + width, y + height);
        long reachSquared = (long) reach * reach;
        long bestScore = Long.MAX_VALUE;

        for (Rect neighbor : neighbors) {
            int left = neighbor.left - gap - width;
            int right = neighbor.right + gap;
            int above = neighbor.top - gap - height;
            int below = neighbor.bottom + gap;
            int alignRight = neighbor.right - width;
            int alignBottom = neighbor.bottom - height;
            List<Anchor> anchors = new ArrayList<>(Arrays.asList(
                    new Anchor(left, neighbor.top, true), new Anchor(left, alignBottom, true),
                    new Anchor(right, neighbor.top, true), new Anchor(right, alignBottom, true),
                    new Anchor(neighbor.left, above, true), new Anchor(alignRight, above, true),
                    new Anchor(neighbor.left, below, true), new Anchor(alignRight, below, true)));
            if (pitch > 0) {
                for (int column = neighbor.left + pitch; column < alignRight; column += pitch) {
                    anchors.add(new Anchor(column, above, true));
                    anchors.add(new Anchor(column, below, true));
                }
                for (int row = neighbor.top + pitch; row < alignBottom; row += pitch) {
                    anchors.add(new Anchor(left, row, true));
                    anchors.add(new Anchor(right, row, true));
                }
            }
            if (snapAlongEdges && y < neighbor.bottom && y + height > neighbor.top) {
                anchors.add(new Anchor(left, y, false));
                anchors.add(new Anchor(right, y, false));
            }
            if (snapAlongEdges && x < neighbor.right && x + width > neighbor.left) {
                anchors.add(new Anchor(x, above, false));
                anchors.add(new Anchor(x, below, false));
            }
            for (Anchor anchor : anchors) {
                int candidateX = anchor.x();
                int candidateY = anchor.y();
                if (candidateX < 0 || candidateX > maxX || candidateY < 0 || candidateY > maxY) continue;
                long dx = (long) candidateX - x;
                long dy = (long) candidateY - y;
                long distance = dx * dx + dy * dy;
                boolean inReach = snapAlongEdges
                        ? Math.abs(dx) <= reach && Math.abs(dy) <= reach
                        : distance <= reachSquared;
                if (!inReach) continue;

                // Prefer lined-up edges over sliding along the edge when both are within reach.
                long score = anchor.aligned() ? distance : distance + reachSquared;
                if (score >= bestScore) continue;
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
                    bestScore = score;
                }
            }
        }
        return best;
    }
}
