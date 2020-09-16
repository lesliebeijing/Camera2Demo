package cn.lesliefang.camera2demo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * 演示 ImageReader 作为输出 Surface 得到 JPEG 或 YUV 数据
 */
public class MainActivity2 extends AppCompatActivity {

    CameraDevice mCameraDevice = null;
    SurfaceView mSurfaceView;
    Surface mSurface;
    HandlerThread mCameraHandlerThread;
    Handler mCameraHandler;
    Size preViewSize = new Size(1280, 720);
    BitmapSurfaceView bitmapSurfaceView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);

        if (ActivityCompat.checkSelfPermission(MainActivity2.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity2.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 101);
        }

        mSurfaceView = findViewById(R.id.surfaceview);
        bitmapSurfaceView = findViewById(R.id.bitmapview);
        mSurfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                mSurface = holder.getSurface();
                if (ActivityCompat.checkSelfPermission(MainActivity2.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(MainActivity2.this, new String[]{Manifest.permission.CAMERA}, 100);
                } else {
                    initCamera();
                }
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                Log.d("leslie", "surfaceView format:" + format + " width:" + width + " height:" + height);
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                mSurface = null;
                if (mCameraDevice != null) {
                    mCameraDevice.close();
                }
            }
        });

        mCameraHandlerThread = new HandlerThread("cameraHandlerTread");
        mCameraHandlerThread.start();
        mCameraHandler = new Handler(mCameraHandlerThread.getLooper());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mCameraHandlerThread != null) {
            mCameraHandlerThread.quitSafely();
        }
    }

    @SuppressLint("MissingPermission")
    private void initCamera() {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        String[] cameraIdList = null;
        try {
            cameraIdList = cameraManager.getCameraIdList();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        if (cameraIdList == null) {
            Log.d("camera", "没有摄像头");
            return;
        }

        String mCameraId = null;
        for (String cameraId : cameraIdList) {
            try {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                // 检查是否有前置摄像头
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    mCameraId = cameraId;
                    /*
                     * 获得相机支持的预览尺寸，可以根据 Surface 的尺寸选择一个最接近的预览尺寸
                     */
                    StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    if (map != null) {
                        Size[] sizeMap = map.getOutputSizes(SurfaceView.class);
                        if (sizeMap != null) {
                            Log.d("leslie", "SurfaceView support size ****************");
                            for (int i = 0; i < sizeMap.length; i++) {
                                Log.d("leslie", sizeMap[i].toString());
                            }
                        }
                        Size[] sizeMap2 = map.getOutputSizes(SurfaceTexture.class);
                        if (sizeMap2 != null) {
                            Log.d("leslie", "SurfaceTexture support size ****************");
                            for (int i = 0; i < sizeMap2.length; i++) {
                                Log.d("leslie", sizeMap2[i].toString());
                            }
                        }
                        Size[] sizeMap3 = map.getOutputSizes(ImageReader.class);
                        if (sizeMap3 != null) {
                            Log.d("leslie", "ImageReader support size ****************");
                            for (int i = 0; i < sizeMap3.length; i++) {
                                Log.d("leslie", sizeMap3[i].toString());
                            }
                        }
                    }
                    break;
                }
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
        if (mCameraId == null) {
            Log.d("camera", "无前置摄像头");
            return;
        }

        try {
            cameraManager.openCamera(mCameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice cameraDevice) {
                    mCameraDevice = cameraDevice;
                    try {
                        List<Surface> surfaceList = new ArrayList<>();
                        surfaceList.add(mSurface);
                        /*
                         * 添加一个 ImageReader 作为输出 surface
                         */
//                        final ImageReader imageReader = ImageReader.newInstance(preViewSize.getWidth(), preViewSize.getHeight(), ImageFormat.JPEG, 2);
//                        imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
//                            @Override
//                            public void onImageAvailable(ImageReader reader) {
//                                Image image = reader.acquireLatestImage();
//                                if (image == null) {
//                                    return;
//                                }
//                                Image.Plane[] planes = image.getPlanes();
//                                for (int i = 0; i < planes.length; i++) {
//                                    Image.Plane plane = planes[i];
//                                    Log.d("leslie", "plan " + i);
//                                    ByteBuffer buffer = plane.getBuffer();
//                                    int pixelStride = plane.getPixelStride();
//                                    int rowStride = plane.getRowStride();
//                                    Log.d("leslie", "buffer size:" + buffer.remaining() + " rowStride:" + rowStride + " pixelStride:" + pixelStride);
//                                    // JPEG 是压缩图片格式所以只有一个 buffer, 直接保存为文件就行了  rowStride=0 pixelStride=0
//
//                                    // 保存一张图片到 SD 卡, 注意写权限。
//                                    // 图片方向不对，需要旋转处理。相机出来的图片是横向的。
//                                    if (!hasSave) {
//                                        hasSave = true;
//                                        File dir = getExternalFilesDir(null);
//                                        File imageFile = new File(dir, "image.jpeg");
//                                        try {
//                                            OutputStream outputStream = new FileOutputStream(imageFile);
//                                            byte[] data = new byte[buffer.remaining()];
//                                            buffer.get(data);
//                                            outputStream.write(data);
//                                            outputStream.close();
//                                            Log.d("leslie", "save image at path " + imageFile.getAbsolutePath());
//                                        } catch (FileNotFoundException e) {
//                                            e.printStackTrace();
//                                        } catch (IOException e) {
//                                            e.printStackTrace();
//                                        }
//                                    }
//                                }
//                                image.close();
//                            }
//                        }, mCameraHandler);

                        final ImageReader imageReader = ImageReader.newInstance(preViewSize.getWidth(), preViewSize.getHeight(), ImageFormat.YUV_420_888, 2);
                        imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                            @Override
                            public void onImageAvailable(ImageReader reader) {
                                Image image = reader.acquireLatestImage();
                                if (image == null) {
                                    return;
                                }

                                int width = image.getWidth();
                                int height = image.getHeight();

                                Image.Plane[] planes = image.getPlanes();
                                Image.Plane yPlane = planes[0];
                                ByteBuffer yBuffer = yPlane.getBuffer();
                                Image.Plane uPlane = planes[1];
                                ByteBuffer uBuffer = uPlane.getBuffer();
                                Image.Plane vPlane = planes[2];
                                ByteBuffer vBuffer = vPlane.getBuffer();

                                byte[] yData = new byte[width * height];
                                yBuffer.get(yData);
                                byte[] uData = new byte[width * height / 2 - 1];
                                uBuffer.get(uData);
                                byte[] vData = new byte[width * height / 2 - 1];
                                vBuffer.get(vData);

                                if (uPlane.getPixelStride() == 1) {
                                    // y u v 之间间隔都是1，像这样  YYYYYYYYUUVV， 转 I420 最方便
                                    byte[] i420Data = new byte[width * height * 3 / 2];
                                    System.arraycopy(yData, 0, i420Data, 0, yData.length); // Y 直接拷贝
                                    System.arraycopy(uData, 0, i420Data, yData.length, uData.length); // 拷贝U
                                    System.arraycopy(vData, 0, i420Data, yData.length + uData.length + 1, vData.length); // 拷贝V
                                } else if (uPlane.getPixelStride() == 2) {
                                    // I/GRALLOC: LockFlexLayout: baseFormat: 11, yStride: 1280, ySize: 921600, uOffset: 921600,  uStride: 1280
                                    // ********* plan 0
                                    // buffer size:921600 rowStride:1280 pixelStride:1
                                    // ********* plan 1
                                    // buffer size:460799 rowStride:1280 pixelStride:2
                                    // ********* plan 2
                                    // buffer size:460799 rowStride:1280 pixelStride:2

                                    // U和U， V和V PixelStride 等于2, U 每行下标  0 2 4 6 取到 U， V 同理。
                                    // 这里转 NV21  :  YYYYYYYYVUVU
                                    byte[] nv21Data = new byte[width * height * 3 / 2];
                                    System.arraycopy(yData, 0, nv21Data, 0, yData.length); // Y 直接拷贝
                                    for (int i = 0; i < uData.length; i = i + 2) {
                                        nv21Data[yData.length + i] = vData[i]; // 0 2 4 6 下标先取  V
                                        nv21Data[yData.length + i + 1] = uData[i]; // 0 2 4 6 下标再取 U
                                    }

                                    try {
                                        // 转Bitmap
                                        YuvImage yuvImage = new YuvImage(nv21Data, ImageFormat.NV21, width, height, null);
                                        ByteArrayOutputStream stream = new ByteArrayOutputStream();
                                        yuvImage.compressToJpeg(new Rect(0, 0, width, height), 80, stream);
                                        Bitmap bitmap = BitmapFactory.decodeByteArray(stream.toByteArray(), 0, stream.size());
                                        stream.close();
                                        if (bitmap != null) {
                                            bitmapSurfaceView.drawBitmap(bitmap);
                                        }
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }
                                image.close();
                            }
                        }, mCameraHandler);
                        surfaceList.add(imageReader.getSurface());

                        cameraDevice.createCaptureSession(surfaceList, new CameraCaptureSession.StateCallback() {
                            @Override
                            public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                                try {
                                    CaptureRequest.Builder builder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW); // 预览
                                    builder.addTarget(mSurface);
                                    builder.addTarget(imageReader.getSurface());
                                    CaptureRequest captureRequest = builder.build();

                                    // 连续发送预览请求
                                    cameraCaptureSession.setRepeatingRequest(captureRequest, new CameraCaptureSession.CaptureCallback() {
                                        @Override
                                        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                                        }

                                        @Override
                                        public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {

                                        }
                                    }, mCameraHandler);

                                } catch (CameraAccessException e) {
                                    e.printStackTrace();
                                }
                            }

                            @Override
                            public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {

                            }
                        }, mCameraHandler);

                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice cameraDevice) {
                    cameraDevice.close();
                }

                @Override
                public void onError(@NonNull CameraDevice cameraDevice, int i) {
                    cameraDevice.close();
                }
            }, mCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == 100 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initCamera();
        }
    }
}