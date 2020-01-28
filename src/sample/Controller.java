package sample;

import com.zkteco.biometric.FingerprintSensorEx;
import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

public class Controller implements Initializable {
    @FXML
    ImageView imageView;
    @FXML
    ImageView image1;
    @FXML
    ImageView image2;
    @FXML
    TextField nameField;
    @FXML
    ImageView veryImageView;
    @FXML
    Label nameLabel;



    //the width of fingerprint image
    private int fpWidth = 0;
    //the height of fingerprint image
    private int fpHeight = 0;
    private byte[] imgbuf;
    private byte[] template = new byte[2048];
    private int[] templateLen = new int[1];
    private long mhDB = 0;
    private long device = 0;
    private byte[] paramValue = new byte[4];
    private int[] size = new int[1];

    private boolean Running = true;

    IntegerProperty scannerProperty = new SimpleIntegerProperty();

    private int count = 1;

    private String fingerPrintTemplateForDB1 = "";
    private String fingerPrintTemplateForDB2 = "";

    Map<Integer, PersonInfo> map =  new HashMap<>();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        scannerProperty.addListener((observable, oldValue, newValue) -> {
            if (newValue.equals(0)) {
                OnCaptureOK(imgbuf);
            }
        });
        initFingerPrint();
        fetchDataFromDB();

    }

    private void initFingerPrint(){
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

        int nFmt = 0;
        FingerprintSensorEx.DBSetParameter(mhDB, 5010, nFmt);

        Running = true;
    }

    private void fetchDataFromDB(){
        String sql = "select * from fingerprintinfo";

        try (Connection connection =  connectToDatabase();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            ResultSet rs = preparedStatement.executeQuery();

            int fid = 1;

            while (rs.next()) {
                int id = rs.getInt("ID");
                String name = rs.getString("Name");
                String temp1 = rs.getString("temp1");
                String temp2 = rs.getString("temp2");

                PersonInfo person = new PersonInfo(id, name);

                byte[] finger1 = new byte[2048];
                byte[] finger2 = new byte[2048];
                FingerprintSensorEx.Base64ToBlob(temp1, finger1, 2048);
                FingerprintSensorEx.Base64ToBlob(temp2, finger2, 2048);

                System.out.println("DBAdd: "+FingerprintSensorEx.DBAdd(mhDB, fid, finger1));
                map.put(fid, person);
                ++fid;
                System.out.println("DBAdd: "+FingerprintSensorEx.DBAdd(mhDB, fid, finger2));
                map.put(fid, person);
                ++fid;
            }

        } catch (SQLException e) {
            printSQLException(e);
        }
    }

    public void onVerify(){
        int[] fid = new int[1];
        int[] score = new int[1];
        System.out.println("Identify: " + FingerprintSensorEx.DBIdentify(mhDB, template,fid, score));

        if (fid[0] != 0 && score[0] > 70){
            PersonInfo personInfo = map.get(fid[0]);

            if (personInfo != null){
                nameLabel.setText("Your name is " + personInfo.name);
            }
        }
    }


    public void onAccept(){
        if (count == 1){
            image1.setImage(imageView.getImage());
            fingerPrintTemplateForDB1 = FingerprintSensorEx.BlobToBase64(template, templateLen[0]);
            ++count;
        }else
        if (count == 2){
            image2.setImage(imageView.getImage());
            fingerPrintTemplateForDB2 = FingerprintSensorEx.BlobToBase64(template, templateLen[0]);
            count = 1;
        }
    }

    public void onSubmit(){
        String sql = "insert into fingerprintinfo (`Name`, `temp1`, `temp2`) values(?,?,?)";

        try (Connection connection = connectToDatabase();
             // Step 2:Create a statement using connection object
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, nameField.getText());
            preparedStatement.setString(2, fingerPrintTemplateForDB1);
            preparedStatement.setString(3, fingerPrintTemplateForDB2);


            System.out.println(preparedStatement);
            // Step 3: Execute the query or update query
            preparedStatement.executeUpdate();
        } catch (SQLException e) {

            // print SQL exception information
            printSQLException(e);
        }
    }

    private   Connection connectToDatabase() throws SQLException {
        String DatabaseUser = "root";
        String DatabasePassword = "";
        String DatabaseUrl = "jdbc:mysql://localhost:3306/fingerprint_test?useSSL=true";
        return DriverManager
                .getConnection(DatabaseUrl, DatabaseUser, DatabasePassword);
    }

    private   void printSQLException(SQLException ex) {
        for (Throwable e: ex) {
            if (e instanceof SQLException) {
                e.printStackTrace(System.err);
                System.err.println("SQLState: " + ((SQLException) e).getSQLState());
                System.err.println("Error Code: " + ((SQLException) e).getErrorCode());
                System.err.println("Message: " + e.getMessage());

                Throwable t = ex.getCause();

                while (t != null) {
                    System.out.println("Cause: " + t);
                    t = t.getCause();
                }
            }
        }
    }

    public void onOpen() {
        initFingerPrint();
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
        number |= ((bytes[1] << 8) & 0xFF00);
        number |= ((bytes[2] << 16) & 0xFF0000);
        number |= ((bytes[3] << 24) & 0xFF000000);
        return number;
    }

    private void OnCaptureOK(byte[] imgBuf) {
        File file = null;
        try {
            writeBitmap(imgBuf, fpWidth, fpHeight);
            file = new File("fingerprint.bmp");
            BufferedImage bufferedImage = ImageIO.read(file);

            Image image = SwingFXUtils.toFXImage(bufferedImage, null);
            imageView.setImage(image);
            veryImageView.setImage(image);
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            if (file != null){
                file.delete();
            }
        }
    }


    private static void writeBitmap(byte[] imageBuf, int nWidth, int nHeight) throws IOException {
        java.io.FileOutputStream fos = new java.io.FileOutputStream("fingerprint.bmp");
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
        int biPlanes = 1;
        int biBitcount = 8;
        int biCompression = 0;
        int biSizeImage = w * nHeight;
        int biXPelsPerMeter = 0;
        int biYPelsPerMeter = 0;
        int biClrUsed = 0;
        int biClrImportant = 0;

        dos.write(changeByte(biSize), 0, 4);
        dos.write(changeByte(nWidth), 0, 4);
        dos.write(changeByte(nHeight), 0, 4);
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

    private static byte[] intToByteArray(final int number) {
        byte[] abyte = new byte[4];

        abyte[0] = (byte) (0xff & number);

        abyte[1] = (byte) ((0xff00 & number) >> 8);
        abyte[2] = (byte) ((0xff0000 & number) >> 16);
        abyte[3] = (byte) ((0xff000000 & number) >> 24);
        return abyte;
    }

    private void FreeSensor() {
        try {        //wait for thread stopping
            Thread.sleep(1000);
        } catch (InterruptedException e) {
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

    private static byte[] changeByte(int data) {
        return intToByteArray(data);
    }

    private static class PersonInfo{
        int id;
        String name;

        PersonInfo(int id, String name){
            this.id = id;
            this.name = name;
        }
    }

}
