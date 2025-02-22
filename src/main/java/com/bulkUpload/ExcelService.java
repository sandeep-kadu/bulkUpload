package com.bulkUpload;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Service
public class ExcelService {
    private final MainTableRepository mainTableRepository;
    private final FailureTableRepository failureTableRepository;

    public ExcelService(MainTableRepository mainTableRepository, FailureTableRepository failureTableRepository) {
        this.mainTableRepository = mainTableRepository;
        this.failureTableRepository = failureTableRepository;
    }

    @Transactional
    public void processExcelFile(MultipartFile file) throws IOException, InvalidFormatException {
        List<MainTable> validRecords = new ArrayList<>();
        List<FailureTable> invalidRecords = new ArrayList<>();

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(inputStream)) {

            Sheet sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; // Skip header row

             // 🔸 Initialize variables to store data
                String name = "";
                String email = "";
                String rawAge = "";  // Store raw age as a string (to capture invalid data)
                Integer age = null;   // Store valid age (if it's a number)
                String errorMessage = "";  // Store validation errors

                try {
                    // 🔹 Read Name (Even if it's empty, we capture it)
                    Cell nameCell = row.getCell(0, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                    name = nameCell.getStringCellValue().trim();

                    // 🔹 Read Email (Same logic)
                    Cell emailCell = row.getCell(1, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                    email = emailCell.getStringCellValue().trim();

                    // 🔹 Read Age (Check if it's a number or invalid text)
                    Cell ageCell = row.getCell(2, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                    if (ageCell.getCellType() == CellType.NUMERIC) {
                        // ✅ If age is a number, store it properly
                        age = (int) ageCell.getNumericCellValue();
                        rawAge = String.valueOf(age);  // Store the original value
                    } else {
                        // ❌ If age is not a number, store the raw value as a string
                        rawAge = ageCell.toString();  
                        errorMessage = "Invalid age format: " + rawAge;
                    }

                    
                    

                    //  Validate the record
                    errorMessage = validateRecord(name, email, age);

                    if (errorMessage == null) {
                        // ✅ Valid record → Save to main table
                        MainTable mainRecord = new MainTable();
                        mainRecord.setName(name);
                        mainRecord.setEmail(email);
                        mainRecord.setAge(age);
                        validRecords.add(mainRecord);
                    } else {
                        // ❌ Invalid record → Save to failure table
                        FailureTable failureRecord = new FailureTable();
                        failureRecord.setName(name);
                        failureRecord.setEmail(email);
                        failureRecord.setAge(rawAge.matches("\\d+") ? Integer.parseInt(rawAge) : null); // 🛠 FIXED
                        failureRecord.setErrorMessage(errorMessage);
                        invalidRecords.add(failureRecord);
                    }
                } catch (Exception e) {
                    // ❌ Critical error while reading row → Capture it as failure
                    FailureTable failureRecord = new FailureTable();
                    failureRecord.setName(name);
                    failureRecord.setEmail(email);
                    failureRecord.setAge(null); // Store as null if error
                    failureRecord.setErrorMessage("Row " + row.getRowNum() + " processing error: " + e.getMessage());
                    invalidRecords.add(failureRecord);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Error reading Excel file: " + e.getMessage());
        }

        // ✅ Save valid and invalid records to DB
        mainTableRepository.saveAll(validRecords);
        failureTableRepository.saveAll(invalidRecords);
    }

    private String validateRecord(String name, String email, int age) {
        if (name == null || name.trim().isEmpty()) return "Name is required";
        if (email == null || !email.contains("@")) return "Invalid email format";
        if (age < 18 || age > 60) return "Age must be between 18 and 60";
        return null;
    }
}
