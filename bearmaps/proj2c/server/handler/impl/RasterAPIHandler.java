package bearmaps.proj2c.server.handler.impl;

import bearmaps.proj2c.AugmentedStreetMapGraph;
import bearmaps.proj2c.server.handler.APIRouteHandler;
import bearmaps.proj2c.utils.Constants;
import spark.Request;
import spark.Response;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.BasicStroke;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static bearmaps.proj2c.utils.Constants.*;

/**
 * Handles requests from the web browser for map images. These images
 * will be rastered into one large image to be displayed to the user.
 *
 * @author rahul, Josh Hug, Tom Xie
 */
public class RasterAPIHandler extends APIRouteHandler<Map<String, Double>, Map<String, Object>> {

    /**
     * Each raster request to the server will have the following parameters
     * as keys in the params map accessible by,
     * i.e., params.get("ullat") inside RasterAPIHandler.processRequest(). <br>
     * ullat : upper left corner latitude, <br> ullon : upper left corner longitude, <br>
     * lrlat : lower right corner latitude,<br> lrlon : lower right corner longitude <br>
     * w : user viewport window width in pixels,<br> h : user viewport height in pixels.
     **/
    private static final String[] REQUIRED_RASTER_REQUEST_PARAMS
            = {"ullat", "ullon", "lrlat", "lrlon", "w", "h"};

    /**
     * The result of rastering must be a map containing all of the
     * fields listed in the comments for RasterAPIHandler.processRequest.
     **/
    private static final String[] REQUIRED_RASTER_RESULT_PARAMS
            = {"render_grid", "raster_ul_lon", "raster_ul_lat", "raster_lr_lon", "raster_lr_lat",
               "depth", "query_success"};


    @Override
    protected Map<String, Double> parseRequestParams(Request request) {
        return getRequestParams(request, REQUIRED_RASTER_REQUEST_PARAMS);
    }

    /**
     * Takes a user query and finds the grid of images that best matches the query. These
     * images will be combined into one big image (rastered) by the front end. <br>
     * <p>
     * The grid of images must obey the following properties, where image in the
     * grid is referred to as a "tile".
     * <ul>
     * <li>The tiles collected must cover the most longitudinal distance per pixel
     * (LonDPP) possible, while still covering less than or equal to the amount of
     * longitudinal distance per pixel in the query box for the user viewport size. </li>
     * <li>Contains all tiles that intersect the query bounding box that fulfill the
     * above condition.</li>
     * <li>The tiles must be arranged in-order to reconstruct the full image.</li>
     * </ul>
     *
     * @param requestParams Map of the HTTP GET request's query parameters - the query box and
     *                      the user viewport width and height.
     * @param response      : Not used by this function. You may ignore.
     * @return A map of results for the front end as specified: <br>
     * "render_grid"   : String[][], the files to display. <br>
     * "raster_ul_lon" : Number, the bounding upper left longitude of the rastered image. <br>
     * "raster_ul_lat" : Number, the bounding upper left latitude of the rastered image. <br>
     * "raster_lr_lon" : Number, the bounding lower right longitude of the rastered image. <br>
     * "raster_lr_lat" : Number, the bounding lower right latitude of the rastered image. <br>
     * "depth"         : Number, the depth of the nodes of the rastered image;
     * can also be interpreted as the length of the numbers in the image
     * string. <br>
     * "query_success" : Boolean, whether the query was able to successfully complete; don't
     * forget to set this to true on success! <br>
     */
    @Override
    public Map<String, Object> processRequest(Map<String, Double> requestParams,
                                              Response response) {
        Map<String, Object> results = new HashMap<>();
        final double rootLonDPP = (ROOT_LRLON - ROOT_ULLON) / TILE_SIZE;
        double ullon = requestParams.get("ullon");
        double lrlon = requestParams.get("lrlon");
        double ullat = requestParams.get("ullat");
        double lrlat = requestParams.get("lrlat");
        if (ullon < ROOT_ULLON && lrlon > ROOT_LRLON && ullat
                > ROOT_ULLAT && lrlat < ROOT_ULLAT) {
            return queryFail();
        }
        if (ullon < ROOT_ULLON) {
            ullon = ROOT_ULLON;
        }
        if (lrlon > ROOT_LRLON) {
            lrlon = ROOT_LRLON;
        }
        if (ullat > ROOT_ULLAT) {
            ullat = ROOT_ULLAT;
        }
        if (lrlat < ROOT_LRLAT) {
            lrlat = ROOT_LRLAT;
        }
        double requestLonDPP = (lrlon - ullon) / requestParams.get("w");
        int depth = 0;
        for (int i = 0; i < 7; i += 1) {
            if (requestLonDPP > rootLonDPP * Math.pow(0.5, i)) {
                depth = i;
                //depthLonDPP = rootLonDPP * Math.pow(0.5, i);
                break;
            }
            depth = 7;
            //depthLonDPP = rootLonDPP * Math.pow(0.5, 6);
        }

        /** @source My friend Anderson Lam gave me significant conceptual help
         *  in figuring out how to create the grid*/
        int numTiles = (int) Math.pow(2, depth);
        double depthLonDPP = (ROOT_LRLON - ROOT_ULLON) / numTiles;
        double depthLatDPP = (ROOT_LRLAT - ROOT_ULLAT) / numTiles;
        int numColumns = (numTiles - (int) ((ullon - ROOT_ULLON) / depthLonDPP)
                - (int) ((ROOT_LRLON - lrlon) / depthLonDPP));
        int numRows = (numTiles - (int) ((ullat - ROOT_ULLAT) / depthLatDPP)
                - (int) ((ROOT_LRLAT - lrlat) / depthLatDPP));
        //System.out.println(numColumns);
        //System.out.println(numRows);
        int startRow = (int) ((ullon - ROOT_ULLON) / depthLonDPP);
        int startColumn = (int) ((ullat - ROOT_ULLAT) / depthLatDPP);
        String[][] grid = new String[numRows][numColumns];
        for (int i = 0; i < numRows; i += 1) {
            for (int j = 0; j < numColumns; j += 1) {
                grid[i][j] = "d" + depth + "_x" + (j + startRow)
                        + "_y" + (i + startColumn) + ".png";
            }
        }
        results.put("render_grid", grid);
        results.put("raster_ul_lon", ROOT_ULLON + depthLonDPP * startRow);
        results.put("raster_ul_lat", ROOT_ULLAT + depthLatDPP * startColumn);
        results.put("raster_lr_lon", ROOT_LRLON - depthLonDPP
                * (int) ((ROOT_LRLON - lrlon) / depthLonDPP));
        results.put("raster_lr_lat", ROOT_LRLAT - depthLatDPP
                * (int) ((ROOT_LRLAT - lrlat) / depthLatDPP));
        results.put("depth", depth);
        results.put("query_success", true);
        return results;
    }

    @Override
    protected Object buildJsonResponse(Map<String, Object> result) {
        boolean rasterSuccess = validateRasteredImgParams(result);

        if (rasterSuccess) {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            writeImagesToOutputStream(result, os);
            String encodedImage = Base64.getEncoder().encodeToString(os.toByteArray());
            result.put("b64_encoded_image_data", encodedImage);
        }
        return super.buildJsonResponse(result);
    }

    private Map<String, Object> queryFail() {
        Map<String, Object> results = new HashMap<>();
        results.put("render_grid", null);
        results.put("raster_ul_lon", 0);
        results.put("raster_ul_lat", 0);
        results.put("raster_lr_lon", 0);
        results.put("raster_lr_lat", 0);
        results.put("depth", 0);
        results.put("query_success", false);
        return results;
    }

    /**
     * Validates that Rasterer has returned a result that can be rendered.
     *
     * @param rip : Parameters provided by the rasterer
     */
    private boolean validateRasteredImgParams(Map<String, Object> rip) {
        for (String p : REQUIRED_RASTER_RESULT_PARAMS) {
            if (!rip.containsKey(p)) {
                System.out.println("Your rastering result is missing the " + p + " field.");
                return false;
            }
        }
        if (rip.containsKey("query_success")) {
            boolean success = (boolean) rip.get("query_success");
            if (!success) {
                System.out.println("query_success was reported as a failure");
                return false;
            }
        }
        return true;
    }

    /**
     * Writes the images corresponding to rasteredImgParams to the output stream.
     * In Spring 2016, students had to do this on their own, but in 2017,
     * we made this into provided code since it was just a bit too low level.
     */
    private void writeImagesToOutputStream(Map<String, Object> rasteredImageParams,
                                           ByteArrayOutputStream os) {
        String[][] renderGrid = (String[][]) rasteredImageParams.get("render_grid");
        int numVertTiles = renderGrid.length;
        int numHorizTiles = renderGrid[0].length;

        BufferedImage img = new BufferedImage(numHorizTiles * Constants.TILE_SIZE,
                numVertTiles * Constants.TILE_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics graphic = img.getGraphics();
        int x = 0, y = 0;

        for (int r = 0; r < numVertTiles; r += 1) {
            for (int c = 0; c < numHorizTiles; c += 1) {
                graphic.drawImage(getImage(Constants.IMG_ROOT + renderGrid[r][c]), x, y, null);
                x += Constants.TILE_SIZE;
                if (x >= img.getWidth()) {
                    x = 0;
                    y += Constants.TILE_SIZE;
                }
            }
        }

        /* If there is a route, draw it. */
        double ullon = (double) rasteredImageParams.get("raster_ul_lon"); //tiles.get(0).ulp;
        double ullat = (double) rasteredImageParams.get("raster_ul_lat"); //tiles.get(0).ulp;
        double lrlon = (double) rasteredImageParams.get("raster_lr_lon"); //tiles.get(0).ulp;
        double lrlat = (double) rasteredImageParams.get("raster_lr_lat"); //tiles.get(0).ulp;

        final double wdpp = (lrlon - ullon) / img.getWidth();
        final double hdpp = (ullat - lrlat) / img.getHeight();
        AugmentedStreetMapGraph graph = SEMANTIC_STREET_GRAPH;
        List<Long> route = ROUTE_LIST;

        if (route != null && !route.isEmpty()) {
            Graphics2D g2d = (Graphics2D) graphic;
            g2d.setColor(Constants.ROUTE_STROKE_COLOR);
            g2d.setStroke(new BasicStroke(Constants.ROUTE_STROKE_WIDTH_PX,
                    BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            route.stream().reduce((v, w) -> {
                g2d.drawLine((int) ((graph.lon(v) - ullon) * (1 / wdpp)),
                        (int) ((ullat - graph.lat(v)) * (1 / hdpp)),
                        (int) ((graph.lon(w) - ullon) * (1 / wdpp)),
                        (int) ((ullat - graph.lat(w)) * (1 / hdpp)));
                return w;
            });
        }

        rasteredImageParams.put("raster_width", img.getWidth());
        rasteredImageParams.put("raster_height", img.getHeight());

        try {
            ImageIO.write(img, "png", os);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private BufferedImage getImage(String imgPath) {
        BufferedImage tileImg = null;
        if (tileImg == null) {
            try {
                File in = new File(imgPath);
                tileImg = ImageIO.read(in);
            } catch (IOException | NullPointerException e) {
                e.printStackTrace();
            }
        }
        return tileImg;
    }
}
