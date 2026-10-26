package org.telegram.messenger;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.core.util.Consumer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Android platform LocationManager implementation without a Play Services dependency. */
@SuppressLint("MissingPermission")
public final class AndroidLocationProvider implements ILocationServiceProvider {
    private LocationManager locationManager;
    private final Map<ILocationListener, List<LocationListener>> activeListeners = new HashMap<>();

    @Override
    public void init(Context context) {
        locationManager = (LocationManager) context.getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
    }
    @Override public ILocationRequest onCreateLocationRequest() { return new AndroidLocationRequest(); }

    @Override
    public IMapApiClient onCreateLocationServicesAPI(Context context, IAPIConnectionCallbacks callbacks, IAPIOnConnectionFailedListener failed) {
        return new IMapApiClient() {
            @Override public void connect() {
                new Handler(Looper.getMainLooper()).post(() -> { if (callbacks != null) callbacks.onConnected(null); });
            }
            @Override public void disconnect() {}
        };
    }
    @Override public boolean checkServices() { return locationManager != null; }

    @Override
    public void getLastLocation(Consumer<Location> callback) {
        if (callback == null) return;
        Location best = null;
        if (locationManager != null) {
            for (String provider : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER}) {
                try {
                    Location candidate = locationManager.getLastKnownLocation(provider);
                    if (candidate != null && (best == null || candidate.getTime() > best.getTime()
                            || (candidate.getTime() == best.getTime() && candidate.getAccuracy() < best.getAccuracy()))) best = candidate;
                } catch (IllegalArgumentException | SecurityException ignored) {}
            }
        }
        callback.accept(best);
    }

    @Override
    public synchronized void requestLocationUpdates(ILocationRequest request, ILocationListener listener) {
        if (locationManager == null || request == null || listener == null) return;
        removeLocationUpdates(listener);
        long interval = Math.max(1000L, ((AndroidLocationRequest) request).interval);
        List<LocationListener> listeners = new ArrayList<>();
        for (String provider : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
            try {
                if (!locationManager.isProviderEnabled(provider)) continue;
                LocationListener nativeListener = new LocationListener() {
                    @Override public void onLocationChanged(Location location) { listener.onLocationChanged(location); }
                    @Override public void onProviderEnabled(String provider) {}
                    @Override public void onProviderDisabled(String provider) {}
                    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
                };
                locationManager.requestLocationUpdates(provider, interval, 0f, nativeListener, Looper.getMainLooper());
                listeners.add(nativeListener);
            } catch (IllegalArgumentException | SecurityException ignored) {}
        }
        if (!listeners.isEmpty()) activeListeners.put(listener, listeners);
    }

    @Override
    public synchronized void removeLocationUpdates(ILocationListener listener) {
        List<LocationListener> listeners = activeListeners.remove(listener);
        if (locationManager == null || listeners == null) return;
        for (LocationListener nativeListener : listeners) {
            try { locationManager.removeUpdates(nativeListener); } catch (SecurityException ignored) {}
        }
    }

    @Override
    public void checkLocationSettings(ILocationRequest request, Consumer<Integer> callback) {
        boolean enabled = false;
        if (locationManager != null) {
            try { enabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER); }
            catch (RuntimeException ignored) {}
        }
        if (callback != null) callback.accept(enabled ? STATUS_SUCCESS : STATUS_SETTINGS_CHANGE_UNAVAILABLE);
    }

    private static final class AndroidLocationRequest implements ILocationRequest {
        private long interval = 5000L;
        @Override public void setPriority(int priority) {}
        @Override public void setInterval(long interval) { this.interval = Math.max(1000L, interval); }
        @Override public void setFastestInterval(long interval) {}
    }
}
