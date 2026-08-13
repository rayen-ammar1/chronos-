package com.chronos.service;

import com.chronos.dto.ForecastResponse;
import com.chronos.dto.ForecastResponse.Point;
import com.chronos.repository.EmployeeTimeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Month;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ForecastService {

    private final EmployeeTimeRepository employeeTimeRepository;

    public ForecastResponse forecastCost(int endYear, int endMonth) {
        List<Double> historyValues = new ArrayList<>();
        List<String> historyLabels = new ArrayList<>();

        // 1. Fetch last 6 months of ACTUAL data
        for (int i = 5; i >= 0; i--) {
            int m = endMonth - i;
            int y = endYear;
            while (m <= 0) { m += 12; y--; }
            
            Double val = employeeTimeRepository.sumTotalManDaysByPeriod(y, m);
            historyValues.add(val != null ? val * 700.0 : 0.0); // Convert man-days to Cost
            historyLabels.add(String.format("%s %d", Month.of(m).toString().substring(0, 3), y));
        }

        // 2. Run Linear Regression (Ordinary Least Squares)
        int n = historyValues.size();
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        for (int i = 0; i < n; i++) {
            double x = i + 1;
            double y = historyValues.get(i);
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
        }

        double denominator = (n * sumX2 - sumX * sumX);
        double slope = (denominator != 0) ? (n * sumXY - sumX * sumY) / denominator : 0;
        double intercept = (sumY - slope * sumX) / n;

        // 3. Calculate Standard Error for Confidence Bands (approx 95%)
        double sumSquaredError = 0;
        for (int i = 0; i < n; i++) {
            double predicted = slope * (i + 1) + intercept;
            double error = historyValues.get(i) - predicted;
            sumSquaredError += error * error;
        }
        double stdError = Math.sqrt(sumSquaredError / Math.max(1, n - 2));
        double margin = 1.96 * stdError; // 95% confidence interval

        // 4. Build History Points
        List<Point> history = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            history.add(new Point(historyLabels.get(i), historyValues.get(i), null, null));
        }

        // 5. Build Forecast Points (Next 3 months)
        List<Point> forecast = new ArrayList<>();
        // Start forecast line from the last historical point to connect the lines visually
        forecast.add(new Point(historyLabels.get(n - 1), historyValues.get(n - 1), null, null)); 
        
        for (int j = 1; j <= 3; j++) {
            int futureIdx = n + j; // x-axis index continues (7, 8, 9)
            double predictedValue = slope * futureIdx + intercept;
            
            // Calculate future label
            int m = endMonth + j;
            int y = endYear;
            while (m > 12) { m -= 12; y++; }
            String label = String.format("%s %d", Month.of(m).toString().substring(0, 3), y);

            forecast.add(new Point(
                label, 
                Math.max(0, predictedValue), 
                Math.max(0, predictedValue - margin), 
                predictedValue + margin
            ));
        }

        return new ForecastResponse(history, forecast);
    }
}