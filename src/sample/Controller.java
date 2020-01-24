package sample;

import com.zkteco.biometric.FingerprintSensorEx;
import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class Controller implements Initializable {
    @FXML
    Button openBtn;
    @FXML
    Button startCaptureBtn;
    @FXML
    Button stopBtn;
    @FXML
    ImageView imageView;

    //the width of fingerprint image
    int fpWidth = 0;
    //the height of fingerprint image
    int fpHeight = 0;
    byte[] imgbuf;
    byte[] template = new byte[2048];
    int[] templateLen = new int[1];
    long mhDB = 0;
    long device = 0;
    byte[] paramValue = new byte[4];
    int[] size = new int[1];
    int nFmt = 0;
    boolean bRegister = false;
    int enroll_idx = 0;
    byte[][] regtemparray = new byte[3][2048];
    int iFid = 1;
    int cbRegTemp = 0;
    byte[] lastRegTemp = new byte[2048];
    boolean bIdentify = true;

    boolean Running = true;

    IntegerProperty scannerProperty = new SimpleIntegerProperty();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        scannerProperty.addListener((observable, oldValue, newValue) -> {
            if (newValue.equals(0)) {
                OnCaptureOK(imgbuf);
            }
        });

    }


    public void onOpen() {
        System.out.println("Init: " + FingerprintSensorEx.Init());

        device = FingerprintSensorEx.OpenDevice(0);
        System.out.println("device: " + device);

        size[0] = 4;
        System.out.println("Parameter: " + FingerprintSensorEx.GetParameters(device, 1, paramValue, size));
        fpWidth = byteArrayToInt(paramValue);
        size[0] = 4;
        FingerprintSensorEx.GetParameters(device, 2, paramValue, size);
        fpHeight = byteArrayToInt(paramValue);

        imgbuf = new byte[fpWidth * fpHeight];

        mhDB = FingerprintSensorEx.DBInit();

        FingerprintSensorEx.DBSetParameter(mhDB, 5010, nFmt);

        Running = true;
    }

    public void onStop() {
        Running = false;
    }

    public void onStart() {


        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                while (Running) {
                    templateLen[0] = 2048;
                    int value = FingerprintSensorEx.AcquireFingerprint(device, imgbuf, template, templateLen);
                    System.out.println("value: " + value);
                    scannerProperty.setValue(value);


                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                Platform.runLater(() -> FreeSensor());
                return null;
            }
        };
        new Thread(task).start();

    }

    public int byteArrayToInt(byte[] bytes) {
        int number = bytes[0] & 0xFF;
        // "|="按位或赋值。
        number |= ((bytes[1] << 8) & 0xFF00);
        number |= ((bytes[2] << 16) & 0xFF0000);
        number |= ((bytes[3] << 24) & 0xFF000000);
        return number;
    }

    private void OnCaptureOK(byte[] imgBuf) {
        File file = null;
        try {
            writeBitmap(imgBuf, fpWidth, fpHeight, "fingerprint.bmp");
            file = new File("fingerprint.bmp");
            BufferedImage bufferedImage = ImageIO.read(file);

            Image image = SwingFXUtils.toFXImage(bufferedImage, null);
            imageView.setImage(image);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }finally {
            if (file != null){
                file.delete();
            }
        }
    }


    public static void writeBitmap(byte[] imageBuf, int nWidth, int nHeight,
                                   String path) throws IOException {
        java.io.FileOutputStream fos = new java.io.FileOutputStream(path);
        java.io.DataOutputStream dos = new java.io.DataOutputStream(fos);

        int w = (((nWidth + 3) / 4) * 4);
        int bfType = 0x424d;
        int bfSize = 54 + 1024 + w * nHeight;
        int bfReserved1 = 0;
        int bfReserved2 = 0;
        int bfOffBits = 54 + 1024;

        dos.writeShort(bfType);
        dos.write(changeByte(bfSize), 0, 4);
        dos.write(changeByte(bfReserved1), 0, 2);
        dos.write(changeByte(bfReserved2), 0, 2);
        dos.write(changeByte(bfOffBits), 0, 4);

        int biSize = 40;
        int biWidth = nWidth;
        int biHeight = nHeight;
        int biPlanes = 1;
        int biBitcount = 8;
        int biCompression = 0;
        int biSizeImage = w * nHeight;
        int biXPelsPerMeter = 0;
        int biYPelsPerMeter = 0;
        int biClrUsed = 0;
        int biClrImportant = 0;

        dos.write(changeByte(biSize), 0, 4);
        dos.write(changeByte(biWidth), 0, 4);
        dos.write(changeByte(biHeight), 0, 4);
        dos.write(changeByte(biPlanes), 0, 2);
        dos.write(changeByte(biBitcount), 0, 2);
        dos.write(changeByte(biCompression), 0, 4);
        dos.write(changeByte(biSizeImage), 0, 4);
        dos.write(changeByte(biXPelsPerMeter), 0, 4);
        dos.write(changeByte(biYPelsPerMeter), 0, 4);
        dos.write(changeByte(biClrUsed), 0, 4);
        dos.write(changeByte(biClrImportant), 0, 4);

        for (int i = 0; i < 256; i++) {
            dos.writeByte(i);
            dos.writeByte(i);
            dos.writeByte(i);
            dos.writeByte(0);
        }

        byte[] filter = null;
        if (w > nWidth) {
            filter = new byte[w - nWidth];
        }

        for (int i = 0; i < nHeight; i++) {
            dos.write(imageBuf, (nHeight - 1 - i) * nWidth, nWidth);
            if (w > nWidth)
                dos.write(filter, 0, w - nWidth);
        }
        dos.flush();
        dos.close();
        fos.close();
    }

    public static byte[] intToByteArray(final int number) {
        byte[] abyte = new byte[4];

        abyte[0] = (byte) (0xff & number);

        abyte[1] = (byte) ((0xff00 & number) >> 8);
        abyte[2] = (byte) ((0xff0000 & number) >> 16);
        abyte[3] = (byte) ((0xff000000 & number) >> 24);
        return abyte;
    }

    public void FreeSensor() {
        try {        //wait for thread stopping
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        if (0 != mhDB) {
            FingerprintSensorEx.DBFree(mhDB);
            mhDB = 0;
        }
        if (0 != device) {
            FingerprintSensorEx.CloseDevice(device);
            device = 0;
        }
        FingerprintSensorEx.Terminate();
    }

    public static byte[] changeByte(int data) {
        return intToByteArray(data);
    }


    private void OnExtractOK(byte[] template, int len) {
        if (bRegister) {
            int[] fid = new int[1];
            int[] score = new int[1];
            int ret = FingerprintSensorEx.DBIdentify(mhDB, template, fid, score);
            if (ret == 0) {
//                textArea.setText("the finger already enroll by " + fid[0] + ",cancel enroll\n");
                bRegister = false;
                enroll_idx = 0;
                return;
            }
            if (enroll_idx > 0 && FingerprintSensorEx.DBMatch(mhDB, regtemparray[enroll_idx - 1], template) <= 0) {
//                textArea.setText("please press the same finger 3 times for the enrollment\n");
                return;
            }
            System.arraycopy(template, 0, regtemparray[enroll_idx], 0, 2048);
            enroll_idx++;
            if (enroll_idx == 3) {
                int[] _retLen = new int[1];
                _retLen[0] = 2048;
                byte[] regTemp = new byte[_retLen[0]];

                if (0 == (ret = FingerprintSensorEx.DBMerge(mhDB, regtemparray[0], regtemparray[1], regtemparray[2], regTemp, _retLen)) &&
                        0 == (ret = FingerprintSensorEx.DBAdd(mhDB, iFid, regTemp))) {
                    iFid++;
                    cbRegTemp = _retLen[0];
                    System.arraycopy(regTemp, 0, lastRegTemp, 0, cbRegTemp);
                    //Base64 Template
//                    textArea.setText("enroll succ:\n");
                } else {
//                    textArea.setText("enroll fail, error code=" + ret + "\n");
                }
                bRegister = false;
            } else {
//                textArea.setText("You need to press the " + (3 - enroll_idx) + " times fingerprint\n");
            }
        } else {
            if (bIdentify) {
                int[] fid = new int[1];
                int[] score = new int[1];
                int ret = FingerprintSensorEx.DBIdentify(mhDB, template, fid, score);
                if (ret == 0) {
//                    textArea.setText("Identify succ, fid=" + fid[0] + ",score=" + score[0] +"\n");
                } else {
//                    textArea.setText("Identify fail, errcode=" + ret + "\n");
                }

            } else {
                if (cbRegTemp <= 0) {
//                    textArea.setText("Please register first!\n");
                } else {
                    int ret = FingerprintSensorEx.DBMatch(mhDB, lastRegTemp, template);
                    if (ret > 0) {
//                        textArea.setText("Verify succ, score=" + ret + "\n");
                    } else {
//                        textArea.setText("Verify fail, ret=" + ret + "\n");
                    }
                }
            }
        }
    }

}
