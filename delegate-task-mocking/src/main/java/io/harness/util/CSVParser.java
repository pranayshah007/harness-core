package io.harness.util;

import com.google.common.io.Resources;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class CSVParser {
    private static final String DELIMITER = ",";
    private static String[] data = null;
    private static String[] rowData = null;
    private static List<DataConfiguration > testCases = new ArrayList<>();
    private static String csvData = "";
    private static String line = "";
    private static DataConfiguration dataConfiguration = new DataConfiguration();
    public static List<DataConfiguration> readFromCSV(String filePath) {
        try {
            byte[] bytes = Resources.toByteArray(CSVParser.class.getClassLoader().getResource(filePath));
            csvData = new String(bytes);
            rowData = csvData.split(System.lineSeparator());
            for (String row:rowData) {
                data = row.split(DELIMITER);
                dataConfiguration.setEnv(data[0]);
                dataConfiguration.setVersion(data[1]);
                dataConfiguration.setAccountId(data[2]);
                dataConfiguration.setToken(data[3]);
                dataConfiguration.setDelegateName(data[4]);
                dataConfiguration.setDelegateCount(Integer.parseInt(data[5]));
                testCases.add(dataConfiguration);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println(testCases);
        return testCases;
    }

    public static List<DataConfiguration> readFromString(String fileContent) {
        String[] lines = fileContent.split(System.lineSeparator());
        List<DataConfiguration> testCases = new ArrayList<>();
        try {
            for (String line : lines) {
                String[] data = line.split(",");
                if (data.length != 4) {
                    throw new Exception("Data is invalid");
                }
                //need to change
                testCases.add(null);
            }
        } catch (Exception ex) {
            log.error("Exception while reading data from csv file");
        }
        return testCases;
    }
}
