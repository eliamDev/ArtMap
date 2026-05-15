package me.Fupery.ArtMap.Painting.Brushes;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import me.Fupery.ArtMap.ArtMap;
import me.Fupery.ArtMap.Painting.Brush;
import me.Fupery.ArtMap.Painting.CachedPixel;
import me.Fupery.ArtMap.Painting.CanvasRenderer;
import me.Fupery.ArtMap.api.Colour.ArtDye;
import me.Fupery.ArtMap.api.Colour.ShadingDye;

public class Fill extends Brush {
    private final ArrayList<CachedPixel> lastFill;
    private int axisLength;
    private Dropper dropper;

    public Fill(CanvasRenderer renderer, Player player, Dropper dropper) {
        super(renderer, player);
        lastFill = new ArrayList<>();
        this.axisLength = getAxisLength();
        this.dropper = dropper;
        cooldownMilli = 350;
    }

    @Override
    public List<CachedPixel> paint(BrushAction action, ItemStack bucket, long strokeTime) {

        if (action == BrushAction.LEFT_CLICK) {
            ArtDye colour =ArtMap.instance().getDyePalette().getDye(this.player.getInventory().getItemInOffHand());

            //handle fill with sponge in offhand
            if(colour == null) {
                ItemStack offhand = this.player.getInventory().getItemInOffHand();
                if(dropper.checkMaterial(offhand) && dropper.getColour() != null) {
                    clean();
                    fillPixel(dropper.getColour());
                    return this.lastFill;
                }
            }
            
            if (colour != null) {
                clean();
                fillPixel(colour);
            }

        } else if (lastFill.size() > 0) {
            for (CachedPixel cachedPixel : lastFill) {
                addPixel(cachedPixel.x, cachedPixel.y, cachedPixel.dye);
            }
        }
        return this.lastFill;
    }

    @Override
    public boolean checkMaterial(ItemStack bucket) {
        return bucket.getType() == Material.BUCKET;
    }

    @Override
    public void clean() {
        lastFill.clear();
    }

    private void fillPixel(ArtDye colour) {
        final byte[] pixel = getCurrentPixel();

        if (pixel != null) {
            final boolean[][] coloured = new boolean[axisLength][axisLength];
            final byte clickedColour = getPixelBuffer()[pixel[0]][pixel[1]];

            if (colour instanceof ShadingDye shadingDye) {
                // Para sombreado: aplicar la transformación a cada píxel individualmente,
                // buscando por color base (ignora el nivel de sombra actual del píxel)
                ArtMap.instance().getScheduler().ASYNC.run(() ->
                        fillBucketShading(coloured, pixel[0], pixel[1], clickedColour, shadingDye));
            } else {
                final byte setColour = colour.getDyeColour(clickedColour);
                ArtMap.instance().getScheduler().ASYNC.run(() ->
                        fillBucket(coloured, pixel[0], pixel[1], clickedColour, setColour));
            }
        }
    }

    private void fillPixel(byte colour) {
        final byte[] pixel = getCurrentPixel();

        if (pixel != null) {

            final boolean[][] coloured = new boolean[axisLength][axisLength];
            final byte clickedColour = getPixelBuffer()[pixel[0]][pixel[1]];
            final byte setColour = colour;

            ArtMap.instance().getScheduler().ASYNC.run(() -> fillBucket(coloured, pixel[0], pixel[1], clickedColour, setColour));
        }
    }

    /**
     * Versión de relleno para ShadingDye (carbón/pluma).
     * Busca píxeles conectados que tengan el mismo color BASE que el píxel clickeado
     * (ignora el nivel de sombra actual), y aplica la transformación de sombreado
     * a cada píxel individualmente según su propio color.
     */
    private void fillBucketShading(boolean[][] coloured, int x, int y, byte sourceColour, ShadingDye shade) {
        if (x < 0 || y < 0 || x >= axisLength || y >= axisLength) return;
        if (coloured[x][y]) return;

        byte currentColour = getPixelBuffer()[x][y];

        // Comparar por color base (dividir entre 4 para ignorar el nivel de sombra)
        int sourceBase  = (sourceColour  & 0xFF) / 4;
        int currentBase = (currentColour & 0xFF) / 4;
        if (currentBase != sourceBase) return;

        byte newColour = shade.getDyeColour(currentColour);
        addPixel(x, y, newColour);
        coloured[x][y] = true;
        lastFill.add(new CachedPixel(x, y, currentColour)); // guarda el color original de ESTE píxel

        fillBucketShading(coloured, x - 1, y, sourceColour, shade);
        fillBucketShading(coloured, x + 1, y, sourceColour, shade);
        fillBucketShading(coloured, x, y - 1, sourceColour, shade);
        fillBucketShading(coloured, x, y + 1, sourceColour, shade);
    }

    private void fillBucket(boolean[][] coloured, int x, int y, byte sourceColour, byte newColour) {
        if (x < 0 || y < 0) {
            return;
        }
        if (x >= axisLength || y >= axisLength) {
            return;
        }

        if (coloured[x][y]) {
            return;
        }

        if (getPixelBuffer()[x][y] != sourceColour) {
            return;
        }
        addPixel(x, y, newColour);
        coloured[x][y] = true;
        lastFill.add(new CachedPixel(x, y, sourceColour));

        fillBucket(coloured, x - 1, y, sourceColour, newColour);
        fillBucket(coloured, x + 1, y, sourceColour, newColour);
        fillBucket(coloured, x, y - 1, sourceColour, newColour);
        fillBucket(coloured, x, y + 1, sourceColour, newColour);
    }
}
