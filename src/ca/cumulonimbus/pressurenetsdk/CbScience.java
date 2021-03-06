package ca.cumulonimbus.pressurenetsdk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import android.location.Location;

/**
 * Science methods and classes. Provide simple data processing like trend
 * discovery. This will eventually expand to forecasting and other atmospheric
 * science algorithms.
 * 
 * @author jacob
 * 
 */
public class CbScience {

	
	/**
	 * Look for a recent change in trend
	 */
	public static String changeInTrend(List<CbObservation> recents) {
		// Reject the request if there's not enough data
		if(recents == null) {
			return "";
		} else if (recents.size() < 3 ) {
			return "";
		}
		//System.out.println("change in trend recents size " + recents.size());
		
		// split up the lists.
		Collections.sort(recents, new TimeComparator());
		List<CbObservation> firstHalf = recents.subList(0, recents.size() / 2);
		List<CbObservation> secondHalf = recents.subList(recents.size() / 2, recents.size() - 1);
		String firstTendency = CbScience.findApproximateTendency(firstHalf);
		String secondTendency = CbScience.findApproximateTendency(secondHalf);
		return firstTendency + "," + secondTendency;
	}
	
	/**
	 * Take a list of recent observations and return their trend
	 * @param recents
	 * @return
	 */
	public static String findApproximateTendency(List<CbObservation> recents) {
		// Reject the request if there's not enough data
		if (recents == null) {
			return "Unknown null";
		}
		if (recents.size() < 3) {
			return "Unknown too small";
		}
		
		// Reject the request if the location coordinates vary too much
		// TODO: future revisions should account for this change and perform
		// the altitude correction in order to use the data rather than bailing
		if (! locationsAreClose(recents)) {
			return "Unknown distance";
		}

		double decision = guessedButGoodDecision(recents);

		//System.out.println("decision  " + decision);
		if (decision > .01) {
			return "Rising";
		} else if (decision <= -.01) {
			return "Falling";
		} else if ((decision >=-.01 ) && (decision <=.01)) {
			return "Steady";
		} else {
			return "Unknown decision " + decision;
		}
	}
	
	/**
	 * Determine if a list of locations are all close by
	 * @param recents
	 * @return
	 */
	private static boolean locationsAreClose(List<CbObservation> recents) {
		double minLat = 90;
		double maxLat = -90;
		double minLon = 180;
		double maxLon = -180;
		for (CbObservation obs : recents ) {
			Location location = obs.getLocation();
			double latitude = location.getLatitude();
			double longitude = location.getLongitude();
			if(latitude > maxLat) {
				maxLat = latitude;
			} 
			if(latitude < minLat) {
				minLat = latitude;
			}
			if(longitude > maxLon) {
				maxLon = longitude;
			}
			if(longitude < minLon) {
				minLon = longitude;
			}
		}
		
		float[] results = new float[2];
		Location.distanceBetween(minLat, minLon, maxLat, maxLon, results);
		float distanceMeters = results[0];
		
		//System.out.println(distanceMeters + "; Locations' proximity for change notification: " + minLat + " to " + maxLat + ", " + minLon + " to " + minLon);

		if(distanceMeters < 2000) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Calculate the slope of a line of best fit
	 * @param recents
	 * @return
	 */
	public static double slopeOfBestFit(List<CbObservation> recents) {
		long time[] = new long[recents.size()];
		double pressure[] = new double[recents.size()];
		int x = 0;
		long sumTime = 0L;
		double sumPressure = 0L;
		for (CbObservation obs : recents) {
			time[x] = obs.getTime() / (1000 * 3600);
			sumTime = sumTime + time[x];
			//sumTime += time[x] * time[x];
			pressure[x] = obs.getObservationValue();
			sumPressure = sumPressure + pressure[x];
			x++;
		}
		long timeBar = sumTime / x;
		double pressureBar = sumPressure / x;
		double ttBar = 0.0; 
		double tpBar = 0.0;
		for (int y = 0; y < x; y++) {
			ttBar += (time[y] - timeBar) * (time[y] - timeBar);
			tpBar += (time[y] - timeBar) * (pressure[y] - pressureBar);
		}
		double beta1 = tpBar / ttBar;
		return beta1;
	}

	/**
	 *  Take a good guess about the recent trends
	 *  to see if they appear meteorological  
	 *   
	 * @param recents
	 * @return
	 */
	public static double guessedButGoodDecision(List<CbObservation> recents) {
		// (TODO: There's too much sorting going on here. Should use min and max)
		// Sort by pressure
		Collections.sort(recents, new SensorValueComparator());
		double minPressure = recents.get(0).getObservationValue();
		double maxPressure = recents.get(recents.size() - 1)
				.getObservationValue();
		// Sort by time
		Collections.sort(recents, new TimeComparator());
		
		return slopeOfBestFit(recents);
	}


	/**
	 * Compare two current conditions' time vales
	 * @author jacob
	 *
	 */
	public static class ConditionTimeComparator implements Comparator<CbCurrentCondition> {
		@Override
		public int compare(CbCurrentCondition o1, CbCurrentCondition o2) {
			if (o1.getTime() < o2.getTime()) {
				return -1;
			} else {
				return 1;
			}
		}
	}
	
	/**
	 * Compare to observation's time values
	 * @author jacob
	 *
	 */
	public static class TimeComparator implements Comparator<CbObservation> {
		@Override
		public int compare(CbObservation o1, CbObservation o2) {
			if (o1.getTime() < o2.getTime()) {
				return -1;
			} else {
				return 1;
			}
		}
	}

	/**
	 * Compare to observation's sensor values
	 * @author jacob
	 *
	 */
	public static class SensorValueComparator implements
			Comparator<CbObservation> {
		@Override
		public int compare(CbObservation o1, CbObservation o2) {
			if (o1.getObservationValue() < o2.getObservationValue()) {
				return -1;
			} else {
				return 1;
			}
		}
	}

}