package com.teeura.schedule;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.hash.Hashing;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class App {

    public static InputStream get_stream(String urlToRead) throws IOException {
        URL url = new URL(urlToRead);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        return conn.getInputStream();
    }

    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (int i = 0; i < hash.length; i++) {
            String hex = Integer.toHexString(0xff & hash[i]);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    public static String schedule(String[] groups) {
        try {
            InputStream inp = get_stream(
                    "https://cloud.nntc.nnov.ru/index.php/s/S5cCa8MWSfyPiqx/download");

            byte[] buffer = new byte[8192];

            // Creating an object of ByteArrayOutputStream class
            ByteArrayOutputStream byteArrayOutputStream
                = new ByteArrayOutputStream();

            // Try block to check for exceptions
            try {
                int temp;

                while ((temp = inp.read(buffer)) != -1) {
                    byteArrayOutputStream.write(buffer, 0, temp);
                }
            } catch (IOException e) {
                System.out.println(e);
            }

            // Mow converting byte array output stream to byte
            // array
            byte[] byteArray
                = byteArrayOutputStream.toByteArray();

            String sha256 = Hashing.sha256()
                .hashBytes(byteArray)
                .toString();

            InputStream strr = new ByteArrayInputStream(byteArray);
            Workbook wb = new XSSFWorkbook(strr);

            Iterator<Sheet> sheets = wb.iterator();
            while (sheets.hasNext()) {
                Sheet sheet = sheets.next();
                String a = getSchedule(sheet, groups);
                a += "\n\nhash: " + sha256;
                return a;
            }
            inp.close();
            wb.close();
        } catch (IOException e) {}
        return "error: parse xlsx table";
    }

    private static LinkedHashSet<String> getAllGroups(Sheet sheet) {
        LinkedHashSet<String> groups = new LinkedHashSet<>();
        Iterator<Row> rows = sheet.rowIterator();

        while (rows.hasNext()) {
            Row row = rows.next();
            Iterator<Cell> cells = row.cellIterator();
            if (cells.hasNext()) {
                String f = cells.next().toString();
                if (f.length() < 16 && f.length() > 2) {
                    if (!f.equals("Группа")) {
                        groups.add(f);
                    }
                }
            }
        }

        return groups;
    }

    private static String formatOutput(ArrayList<String> rowCeils) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < (rowCeils.size() - rowCeils.size() % 3); i += 3) {
            String time = rowCeils.get(i);
            String room = rowCeils.get(i + 1);

            ArrayList<String> t = new ArrayList<>();
            Matcher mat = Pattern.compile("[0-9]{1,}").matcher(time);
            while (mat.find()) {
                t.add(mat.group().toString());
            }

            if (t.size() == 4) {
                time = String.format("[%s:%s][%s:%s]", t.get(0), t.get(1), t.get(2), t.get(3));
            } else {
                time = "[None]";
            }

            String[] spl = rowCeils.get(i + 2).split("/");

            // if (!spl[0].trim().isEmpty()) {
                if (spl.length == 2) {
                    sb.append(String.format("[%d] -> %s -> %s / %s\n\r    %s\n\n", (i / 3) + 1, time, room, spl[1].trim(), spl[0].trim()));
                }
            // }

        }
        if (rowCeils.size() % 3 != 0) {
            int e = rowCeils.size() - rowCeils.size() % 3;
            sb.append("warning: the number of cells in line is not divisible by 3 without a remainder, schedule may not display correctly, dump raw:\n");
            for (int curent = e; curent < rowCeils.size(); curent++) {
                 sb.append("hex: ");
                 sb.append(bytesToHex(rowCeils.get(e).getBytes()));
                 sb.append("\n");
                 sb.append("txt: ");
                 sb.append(rowCeils.get(e).toCharArray());
                 sb.append("\n");
            }
        }
        return sb.toString();
    }

    // private static String rawOutput(ArrayList<String> rowCeils) {

    //     return "raw output: TODO\n";
    // }

    private static String formatOutputCheckIsDay(Cell cell) {
        Pattern pattern = Pattern.compile("[0-9]{1,}.*[а-яА-Я]{1,}.*[0-9]{1,}.*\\([а-яА-Я]{1,}\\)");
        Matcher matcher = pattern.matcher(cell.toString());
        if (matcher.find()) {
            String str = matcher.group().toString().replaceAll("\\.", "");
            return str.replaceAll("\\x{A0}", "");
        }

        return null;
    }

    private static ArrayList<String> cellsToStrings(Iterator<Cell> cells) {
        ArrayList<String> cel = new ArrayList<>();
        while (cells.hasNext()) {
            Cell c = cells.next();
            switch (c.getCellType()) {
                case STRING:
                    cel.add(c.getStringCellValue());
                    break;
                case NUMERIC:
                    cel.add(String.valueOf(((int)c.getNumericCellValue())));
                    break;
                default:
                    break;
            }
        }
        return cel;
    }

    private static String getSchedule(Sheet sheet, String[] groups) {
        StringBuilder sb = new StringBuilder();
        Iterator<Row> rows = sheet.rowIterator();
        while (rows.hasNext()) {
            Row row = rows.next();
            Iterator<Cell> cells = row.cellIterator();
            if (cells.hasNext()) {
                Cell cell = cells.next();

                String day = formatOutputCheckIsDay(cell);
                if (day != null) {
                    sb.append(day);
                    sb.append('\n');
                    continue;
                }

                for (String group : groups) {
                    if (cell.toString().equals(group)) {
                        sb.append(String.format("[%s]", cell.toString())); // add name of group
                        sb.append('\n');

                        ArrayList<String> cel = cellsToStrings(cells); // lessons (RAW)
                        sb.append(formatOutput(cel));
                    }
                }
            }
        }
        return sb.toString();
    }
}

