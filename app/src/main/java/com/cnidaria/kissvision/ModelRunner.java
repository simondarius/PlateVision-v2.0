package com.cnidaria.kissvision;
import android.content.Context;

import org.tensorflow.lite.Interpreter;

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.io.FileInputStream;

import java.nio.channels.FileChannel;

import android.content.res.AssetFileDescriptor;
import android.util.Log;

public class ModelRunner {
    private Interpreter tflite;

    public ModelRunner(Context context) throws IOException {
        tflite = new Interpreter(loadModelFile(context));
    }

    private MappedByteBuffer loadModelFile(Context context) throws IOException {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd("model.tflite");
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    public float[] runInference(float[][][][] inputData) {
        float[][] outputData = new float[1][4];
        tflite.run(inputData, outputData);
        return outputData[0];
    }

    public void close() {
        tflite.close();
    }
}
