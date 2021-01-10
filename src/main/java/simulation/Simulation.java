package simulation;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pojo.HardwareParams;
import tools.CalcSunriseSunset;
import tools.DecimalTimeToMinutes;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public class Simulation {

    private final Logger log = LoggerFactory.getLogger(Simulation.class);
    private final DecimalTimeToMinutes timeToMinutes;
    private final DateTimeFormatter formatter;
    private long TOTAL_ERROR_SECONDS = 0;

    public Simulation() {
        this.timeToMinutes = new DecimalTimeToMinutes();
        this.formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    }

    public int getNumberOfDaysOnBatteryStartingBy(int year, int month, int day, HardwareParams hp, int timezone, double latitude, double longitude) {
        CalcSunriseSunset calcSunriseSunset = new CalcSunriseSunset(timezone, latitude, longitude);
        double sun = calcSunriseSunset.getSunrise(year, month, day);
        LocalDateTime startTime = LocalDateTime.of(year, month, day, (int) sun, timeToMinutes.calcMinutesWithoutHours(sun));
        LocalDateTime endTime = LocalDateTime.from(startTime);

        log.info("Starting simulation with full battery on " + formatter.format(startTime));

        int dayCounter = 0;
        double batteryWh = hp.getBatteryWh();
        double batteryChargingPower = hp.getBatteryChargingPower();
        double minerPowerConsumption = hp.getMinerPowerConsumption();

        while (batteryWh > 0) {
            double sunrise = calcSunriseSunset.getSunrise(endTime.getYear(), endTime.getMonthValue(), endTime.getDayOfMonth());

            double darkMinutes = calcDarkMinutes(calcSunriseSunset, endTime);
            double lightMinutes = calcLightMinutes(calcSunriseSunset, endTime);

            System.out.println("Sunrise: " + sunrise + " time: " + formatter.format(endTime));

            // repairing low accuracy error (probably due to calculating decimal to minutes)
            LocalDateTime copy = LocalDateTime.from(endTime);
            endTime = endTime.withHour((int) sunrise);
            endTime = endTime.withMinute(timeToMinutes.calcMinutesWithoutHours(sunrise));
            TOTAL_ERROR_SECONDS += Math.abs(endTime.toEpochSecond(ZoneOffset.ofHours(1)) - copy.toEpochSecond(ZoneOffset.ofHours(1)));

            // minutes of day
            while (lightMinutes > 0) {
                batteryWh = batteryWh + (1 / 60d) * batteryChargingPower;
                if (batteryWh > hp.getBatteryWh()) {
                    batteryWh = hp.getBatteryWh(); // battery cannot be more than 100% full
                }
                lightMinutes--;
                endTime = endTime.plusMinutes(1);

            }
            //System.out.println("Sunset: "+sunset+" time: "+formatter.format(endTime));

            // minutes of night
            while (darkMinutes > 0) {
                batteryWh = batteryWh - (1 / 60d) * minerPowerConsumption;
                if (batteryWh <= 0) {
                    break; // battery is at 0%
                }
                darkMinutes--;
                endTime = endTime.plusMinutes(1);

            }

            dayCounter++;
        }

        log.info("Start time: " + formatter.format(startTime) + " | end time: " + formatter.format(endTime));
        log.info("Total days: " + dayCounter);
        log.info("Total error: " + TOTAL_ERROR_SECONDS / 60 + " minutes");
        return dayCounter;
    }

    private double calcDarkMinutes(CalcSunriseSunset calcSunriseSunset, LocalDateTime endTime) {
        double sunset = calcSunriseSunset.getSunset(endTime.getYear(), endTime.getMonthValue(), endTime.getDayOfMonth());

        endTime = endTime.plusDays(1);
        double nextSunrise = calcSunriseSunset.getSunrise(endTime.getYear(), endTime.getMonthValue(), endTime.getDayOfMonth());
        endTime = endTime.minusDays(1);

        return ((24d - sunset + nextSunrise) * 60);
    }

    private double calcLightMinutes(CalcSunriseSunset calcSunriseSunset, LocalDateTime endTime) {
        double sunrise = calcSunriseSunset.getSunrise(endTime.getYear(), endTime.getMonthValue(), endTime.getDayOfMonth());
        double sunset = calcSunriseSunset.getSunset(endTime.getYear(), endTime.getMonthValue(), endTime.getDayOfMonth());

        double timeOfTheLight = sunset - sunrise;
        return (timeOfTheLight * 60);
    }

}
