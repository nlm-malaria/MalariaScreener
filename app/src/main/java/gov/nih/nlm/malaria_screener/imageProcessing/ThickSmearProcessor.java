package gov.nih.nlm.malaria_screener.imageProcessing;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Environment;
import android.util.Log;

import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Rect;

import java.io.File;
import java.io.IOException;

import gov.nih.nlm.malaria_screener.custom.Utils.UtilsCustom;
import gov.nih.nlm.malaria_screener.custom.Utils.UtilsData;

public class ThickSmearProcessor {

    private static final String TAG = "MyDebug";

    Mat oriSizeMat;

    int inputSize = 44;

    Bitmap canvasBitmap;

    Bitmap output;

    int num_th = 400;

    int batch_size = UtilsCustom.batch_size;

    Mat candi_patches = new Mat(); // concatenated parasite candidate patches

    Mat extra_Mat = new Mat();
    Bitmap exraBitmap;

    public ThickSmearProcessor(Mat oriSizeMat) {

        this.oriSizeMat = oriSizeMat;
    }

    public void processImage() {

        // reset current parasites and WBC counts
        UtilsData.resetCurrentCounts_thick();

        //read deep learning model from assets folder
        //String dnnModel = getPath("ThickSmearModel.h5.pb", getApplicationContext());

        int[] x = new int[num_th];
        int[] y = new int[num_th];

        long startTime = System.currentTimeMillis();

        int wbc_num = processThickImage(oriSizeMat.getNativeObjAddr(), candi_patches.getNativeObjAddr(), x, y, extra_Mat.getNativeObjAddr());

        // added for debug------------

        /*exraBitmap = Bitmap.createBitmap(extra_Mat.width(), extra_Mat.height(), Bitmap.Config.RGB_565);
        Utils.matToBitmap(extra_Mat, exraBitmap);

        OutputStream outStream = null;
        File file = null;
        try {
            file = createImageFileExtra();
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            outStream = new FileOutputStream(file);
            exraBitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream);
            outStream.flush();
            outStream.close();
        } catch(Exception e) {

        }*/
        //------------------------------------------

        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;
        Log.d(TAG, "Greedy method Time: " + totalTime);

        // set Bitmap to paint
        canvasBitmap = Bitmap.createBitmap(oriSizeMat.width(), oriSizeMat.height(), Bitmap.Config.RGB_565);
        Utils.matToBitmap(oriSizeMat, canvasBitmap);

        Canvas canvas = new Canvas(canvasBitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStrokeWidth(5);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.RED);

        //classify image patches
        int parasiteNum = 0;

        UtilsCustom.results.clear();

        int patch_num = candi_patches.height()/inputSize;

        int iteration = patch_num / batch_size;
        int lastBatchSize = patch_num % batch_size;

        float[] floatPixels = new float[inputSize * inputSize * 3 * batch_size];
        float[] floatPixels_last = new float[inputSize * inputSize * 3 * lastBatchSize];

        // normal batches
        for (int i = 0; i < iteration; i++) {

            for (int n = 0; n < batch_size; n++) {

                floatPixels = putInPixels(i, n, floatPixels);

            }

            UtilsCustom.tensorFlowClassifier_thick.recongnize_batch_thick(floatPixels, batch_size);
        }

        // last batch
        for (int n = 0; n < lastBatchSize; n++) {

            floatPixels_last = putInPixels(iteration, n, floatPixels_last);
        }

        UtilsCustom.tensorFlowClassifier_thick.recongnize_batch_thick(floatPixels, batch_size);

        // draw results on image
        for (int i=0; i <patch_num; i++){

            if (UtilsCustom.results.get(i)==1) {
                parasiteNum++;
                paint.setColor(Color.RED);
                canvas.drawCircle(x[i], y[i], 20, paint);
            } else {
                    /*paint.setColor(Color.BLUE);
                    canvas.drawCircle(x[i], y[i], 20, paint);*/
            }
        }

        // save results
        UtilsData.parasiteCurrent = parasiteNum;
        UtilsData.WBCCurrent = wbc_num;
        UtilsData.parasiteTotal = UtilsData.parasiteTotal + parasiteNum;
        UtilsData.WBCTotal = UtilsData.WBCTotal + wbc_num;
        UtilsData.addParasiteCount(String.valueOf(parasiteNum));
        UtilsData.addWBCCount(String.valueOf(wbc_num));

        //save result bitmap to memory
        //UtilsCustom.resultBitmap = canvasBitmap;

        // result.convertTo(result, CvType.CV_8U);
        //output = Bitmap.createBitmap(candi_patches.cols(), candi_patches.rows(), Bitmap.Config.RGB_565);
        //Utils.matToBitmap(candi_patches, output);

        candi_patches.release();

    }

    private float[] putInPixels(int i, int n, float[] floatPixels) {

        Bitmap chip_bitmap;
        int[] intPixels;

        Rect rect = new Rect(0, (i * batch_size + n) * inputSize, inputSize, inputSize);
        Mat temp = new Mat(candi_patches, rect);

        temp.convertTo(temp, CvType.CV_8U);
        chip_bitmap = Bitmap.createBitmap(temp.cols(), temp.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(temp, chip_bitmap);

        intPixels = new int[inputSize * inputSize];
        chip_bitmap.getPixels(intPixels, 0, chip_bitmap.getWidth(), 0, 0, chip_bitmap.getWidth(), chip_bitmap.getHeight());

        for (int j = 0; j < intPixels.length; ++j) {
            floatPixels[n * inputSize * inputSize * 3 + j * 3 + 0] = ((intPixels[j] >> 16) & 0xFF) / 255.0f; //R
            floatPixels[n * inputSize * inputSize * 3 + j * 3 + 1] = ((intPixels[j] >> 8) & 0xFF) / 255.0f;  //G
            floatPixels[n * inputSize * inputSize * 3 + j * 3 + 2] = (intPixels[j] & 0xFF) / 255.0f;         //B
        }

        return floatPixels;
    }

    /*OutputStream outStream = null;
                File file = null;
                try {
                    file = createImageFile(i);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                try {
                    outStream = new FileOutputStream(file);
                    chip_bitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream);
                    outStream.flush();
                    outStream.close();
                } catch(Exception e) {

                }*/

    private File createImageFile(int i) throws IOException {

        File direct = new File(Environment.getExternalStorageDirectory(), "NLM_Malaria_Screener/patches");

        if (!direct.exists()) {
            direct.mkdirs();
        }


        File imgFile = new File(new File(Environment.getExternalStorageDirectory(), "NLM_Malaria_Screener/patches"), i + ".png");

        return imgFile;
    }

    private File createImageFileExtra() throws IOException {

        File imgFile = new File(Environment.getExternalStorageDirectory(), "this_extra.png");

        return imgFile;
    }


    public Bitmap getResultBitmap() {
        return canvasBitmap;
    }


    public native int processThickImage(long mat, long result, int[] x, int[] y, long extraMat);
}