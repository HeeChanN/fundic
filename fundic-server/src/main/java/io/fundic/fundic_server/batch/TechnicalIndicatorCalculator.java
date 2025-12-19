package io.fundic.fundic_server.batch;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TechnicalIndicatorCalculator {

    public TechnicalIndicators calculate(List<Integer> closePrices) {
        if (closePrices == null || closePrices.isEmpty()) {
            return new TechnicalIndicators(0.0, 0.0, 0.0, 0.0);
        }

        double rsi = calculateRSI(closePrices, 14);
        double macd = calculateMACD(closePrices);
        double ma20 = calculateMA(closePrices, 20);
        double ma60 = calculateMA(closePrices, 60);

        return new TechnicalIndicators(rsi, macd, ma20, ma60);
    }

    private double calculateRSI(List<Integer> prices, int period) {
        if (prices.size() < period + 1) {
            return 50.0;  // Default neutral value
        }

        double gainSum = 0.0;
        double lossSum = 0.0;

        for (int i = 0; i < period; i++) {
            double change = prices.get(i) - prices.get(i + 1);
            if (change > 0) {
                gainSum += change;
            } else {
                lossSum += Math.abs(change);
            }
        }

        double avgGain = gainSum / period;
        double avgLoss = lossSum / period;

        if (avgLoss == 0) return 100.0;

        double rs = avgGain / avgLoss;
        return 100.0 - (100.0 / (1.0 + rs));
    }

    private double calculateMACD(List<Integer> prices) {
        // MACD = EMA(12) - EMA(26)
        if (prices.size() < 26) {
            return 0.0;
        }

        double ema12 = calculateEMA(prices, 12);
        double ema26 = calculateEMA(prices, 26);

        return ema12 - ema26;
    }

    private double calculateEMA(List<Integer> prices, int period) {
        if (prices.size() < period) {
            return prices.get(0).doubleValue();
        }

        double multiplier = 2.0 / (period + 1);
        double ema = prices.subList(0, period).stream()
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0.0);

        for (int i = period; i < prices.size(); i++) {
            ema = (prices.get(i) - ema) * multiplier + ema;
        }

        return ema;
    }

    private double calculateMA(List<Integer> prices, int period) {
        if (prices.size() < period) {
            period = prices.size();
        }

        if (period == 0) {
            return 0.0;
        }

        return prices.subList(0, period).stream()
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0.0);
    }

    public record TechnicalIndicators(
            double rsi,
            double macd,
            double ma20,
            double ma60
    ) {
    }
}
