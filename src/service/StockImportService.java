package service;

import dao.StockDAO;
import model.Stock;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for importing master Indian stock market data from CSV file.
 * Architecture workflow:
 * CSV File -> Java StockImportService -> StockDAO -> JDBC -> MySQL
 * Demonstrates: File I/O, Collections (List<Stock>), Parsing, and JDBC Batch Inserts.
 * Fulfills Stock Data Import & Core Java requirements for GUVI Evaluation.
 */
public class StockImportService {

    private final StockDAO stockDAO;

    public StockImportService() {
        this.stockDAO = new StockDAO();
    }

    public StockImportService(StockDAO stockDAO) {
        this.stockDAO = stockDAO;
    }

    /**
     * Reads Indian stocks from CSV and persists them into the database using JDBC batching.
     * @param csvPath Path to the CSV master file
     * @return Number of stocks successfully imported
     */
    public int importStocksFromCSV(String csvPath) throws IOException, SQLException {
        File file = new File(csvPath);
        if (!file.exists()) {
            System.err.println("[StockImportService] CSV file not found at: " + csvPath);
            return 0;
        }

        List<Stock> stocksToImport = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            boolean isHeader = true;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                if (isHeader) {
                    isHeader = false;
                    // Skip header line (e.g. symbol,company_name,exchange,price,available_quantity,status)
                    if (line.toLowerCase().contains("symbol")) continue;
                }

                String[] parts = line.split(",");
                if (parts.length >= 5) {
                    try {
                        String symbol = parts[0].trim().toUpperCase();
                        String companyName = parts[1].trim();
                        String exchange = parts[2].trim().toUpperCase();
                        double price = Double.parseDouble(parts[3].trim());
                        int quantity = Integer.parseInt(parts[4].trim());
                        String status = (parts.length >= 6) ? parts[5].trim().toUpperCase() : "ACTIVE";

                        Stock stock = new Stock(symbol, companyName, exchange, price, quantity, status);
                        stocksToImport.add(stock);
                    } catch (NumberFormatException nfe) {
                        System.err.println("[StockImportService] Skipping malformed row: " + line);
                    }
                }
            }
        }

        System.out.printf("[StockImportService] Parsed %d Indian stocks from %s. Executing JDBC batch insert...\n",
                stocksToImport.size(), csvPath);

        int inserted = stockDAO.insertBatch(stocksToImport);
        System.out.printf("[StockImportService] Successfully imported %d stocks into relational database.\n", inserted);
        return inserted;
    }
}
