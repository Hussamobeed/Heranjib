/**
 * Google Apps Script Backend for Hotspot Voucher Manager Android App
 * 
 * This script receives HTTPS POST requests from the Android application
 * and writes the data into Google Sheets.
 */

// Define sheet names
const SHEET_TRANSACTIONS = "Transactions";
const SHEET_CATEGORIES = "VoucherCategories";
const SHEET_STATS = "DailyStatistics";
const SHEET_LOGS = "ApplicationLogs";

/**
 * Handles GET requests (e.g., when opening the Web App URL in a browser).
 * Required to prevent the "Script function not found: doGet" error when testing the URL.
 */
function doGet(e) {
  return ContentService.createTextOutput("Hotspot Voucher Manager Backend is running successfully. Please send POST requests to this endpoint.");
}

/**
 * Handles POST requests from the Android application.
 */
function doPost(e) {
  try {
    // 1. Validate the POST data
    if (!e || !e.postData || !e.postData.contents) {
      return createJsonResponse({ success: false, message: "No data received" });
    }

    // 2. Parse the JSON request
    let data;
    try {
      data = JSON.parse(e.postData.contents);
    } catch (parseError) {
      return createJsonResponse({ success: false, message: "Invalid JSON format" });
    }
    
    // Use LockService to prevent concurrent writes causing data collision
    const lock = LockService.getScriptLock();
    // Wait for up to 30 seconds for other processes to finish.
    lock.waitLock(30000); 

    try {
      if (data.type === 'transaction') {
         handleTransaction(data.payload);
      } else if (data.type === 'log') {
         handleLog(data.payload);
      } else {
         return createJsonResponse({ success: false, message: "Unknown data type" });
      }
    } finally {
      // Release the lock so other executions can continue.
      lock.releaseLock();
    }
    
    // 8. Return JSON response
    return createJsonResponse({ success: true });

  } catch (error) {
    // Return error if something goes wrong
    return createJsonResponse({ success: false, message: error.message });
  }
}

/**
 * Helper to process a transaction record.
 */
function handleTransaction(payload) {
    const spreadsheet = SpreadsheetApp.getActiveSpreadsheet();
    const txSheet = getOrCreateSheet(spreadsheet, SHEET_TRANSACTIONS, [
      "TransactionID", "Date", "Time", "CustomerPhone", "Amount", 
      "VoucherCategory", "VoucherNumber", "SMSStatus", "OriginalSMS"
    ]);

    // 9. Ignore duplicate TransactionID
    const dataRange = txSheet.getDataRange();
    const values = dataRange.getValues();
    const txIdIndex = 0; // TransactionID is the first column
    for (let i = 1; i < values.length; i++) {
       if (values[i][txIdIndex] == payload.TransactionID) {
           // Duplicate found, ignore
           return;
       }
    }

    // Append to Transactions sheet
    txSheet.appendRow([
      payload.TransactionID,
      payload.Date,
      payload.Time,
      payload.CustomerPhone,
      payload.Amount,
      payload.VoucherCategory,
      payload.VoucherNumber,
      payload.SMSStatus,
      payload.OriginalSMS
    ]);

    // 5. Update VoucherCategories sheet
    updateCategory(spreadsheet, payload);

    // 6. Update DailyStatistics
    updateDailyStats(spreadsheet, payload);
}

/**
 * Helper to update VoucherCategories sheet.
 */
function updateCategory(spreadsheet, payload) {
    const catSheet = getOrCreateSheet(spreadsheet, SHEET_CATEGORIES, [
      "Category", "Total", "Available", "Sent", "Revenue", "LastTransaction"
    ]);
    
    const dataRange = catSheet.getDataRange();
    const values = dataRange.getValues();
    let rowIndex = -1;
    
    // Find the category
    for (let i = 1; i < values.length; i++) {
        if (values[i][0] == payload.VoucherCategory) {
            rowIndex = i + 1; // 1-based indexing for sheets, plus header row
            break;
        }
    }
    
    if (rowIndex > -1) {
        // Update existing row
        let currentSent = Number(values[rowIndex-1][3]) || 0;
        let currentAvailable = Number(values[rowIndex-1][2]) || 0;
        let currentRevenue = Number(values[rowIndex-1][4]) || 0;
        
        catSheet.getRange(rowIndex, 3).setValue(Math.max(0, currentAvailable - 1)); // Available
        catSheet.getRange(rowIndex, 4).setValue(currentSent + 1); // Sent
        catSheet.getRange(rowIndex, 5).setValue(currentRevenue + Number(payload.Amount)); // Revenue
        catSheet.getRange(rowIndex, 6).setValue(payload.Date + " " + payload.Time); // LastTransaction
    } else {
        // Create new category row if it doesn't exist
        catSheet.appendRow([
            payload.VoucherCategory,
            1, // Total (assuming 1 for now)
            0, // Available
            1, // Sent
            Number(payload.Amount), // Revenue
            payload.Date + " " + payload.Time // LastTransaction
        ]);
    }
}

/**
 * Helper to update DailyStatistics sheet.
 */
function updateDailyStats(spreadsheet, payload) {
    const statSheet = getOrCreateSheet(spreadsheet, SHEET_STATS, [
      "Date", "SalesCount", "Revenue", "SMSSuccess", "SMSFailed"
    ]);
    
    const dataRange = statSheet.getDataRange();
    const values = dataRange.getValues();
    let rowIndex = -1;
    
    // Find the date
    for (let i = 1; i < values.length; i++) {
        if (values[i][0] == payload.Date) {
            rowIndex = i + 1;
            break;
        }
    }
    
    let isSuccess = payload.SMSStatus === "SUCCESS" ? 1 : 0;
    let isFailed = payload.SMSStatus === "FAILED" ? 1 : 0;
    
    if (rowIndex > -1) {
        // Update existing date
        let currentSales = Number(values[rowIndex-1][1]) || 0;
        let currentRevenue = Number(values[rowIndex-1][2]) || 0;
        let currentSuccess = Number(values[rowIndex-1][3]) || 0;
        let currentFailed = Number(values[rowIndex-1][4]) || 0;
        
        statSheet.getRange(rowIndex, 2).setValue(currentSales + 1);
        statSheet.getRange(rowIndex, 3).setValue(currentRevenue + Number(payload.Amount));
        statSheet.getRange(rowIndex, 4).setValue(currentSuccess + isSuccess);
        statSheet.getRange(rowIndex, 5).setValue(currentFailed + isFailed);
    } else {
        // Append new date row
        statSheet.appendRow([
            payload.Date,
            1, // SalesCount
            Number(payload.Amount), // Revenue
            isSuccess, // SMSSuccess
            isFailed   // SMSFailed
        ]);
    }
}

/**
 * Helper to process an application log.
 */
function handleLog(payload) {
    const spreadsheet = SpreadsheetApp.getActiveSpreadsheet();
    const logSheet = getOrCreateSheet(spreadsheet, SHEET_LOGS, [
      "Date", "Time", "Type", "Message"
    ]);
    
    logSheet.appendRow([
      payload.Date,
      payload.Time,
      payload.Type,
      payload.Message
    ]);
}

/**
 * Helper to format JSON responses.
 */
function createJsonResponse(responseObject) {
  return ContentService.createTextOutput(JSON.stringify(responseObject))
    .setMimeType(ContentService.MimeType.JSON);
}

/**
 * Utility to get a sheet by name or create it with headers if it doesn't exist.
 */
function getOrCreateSheet(spreadsheet, sheetName, headers) {
    let sheet = spreadsheet.getSheetByName(sheetName);
    if (!sheet) {
        sheet = spreadsheet.insertSheet(sheetName);
        if (headers && headers.length > 0) {
            sheet.appendRow(headers);
            // Freeze the header row and make it bold
            sheet.setFrozenRows(1);
            sheet.getRange(1, 1, 1, headers.length).setFontWeight("bold");
        }
    }
    return sheet;
}
