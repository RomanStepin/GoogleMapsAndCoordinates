package com.example.googlemapandcoordinates;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import java.security.Permission;
import java.util.Objects;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, LoaderManager.LoaderCallbacks<Cursor> {

    static final String AUTHORITY = "AssetsBaseContentProvider";  // константы для Uri content provider -a
    static final String MUSHROOMS_PATH = "myMushrooms";

    static final String DESCRIPTION_COLUMN_NAME = "descriptions"; // имена полей в таблице
    static final String COORDINATE_X_COLUMN_NAME = "coordinate_x";
    static final String COORDINATE_Y_COLUMN_NAME = "coordinate_y";

    SimpleCursorAdapter simpleCursorAdapter;
    LoaderManager loaderManager;
    ListView listView;

    private GoogleMap mMap;
    UiSettings uiSettings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        assert mapFragment != null;
        mapFragment.getMapAsync(this);

        listView = findViewById(R.id.list);
        simpleCursorAdapter = new SimpleCursorAdapter(this, android.R.layout.simple_list_item_1,null,new String[]{DESCRIPTION_COLUMN_NAME}, new int[]{android.R.id.text1},0);
        listView.setAdapter(simpleCursorAdapter);
        loaderManager = LoaderManager.getInstance(this);
        Bundle bundle = new Bundle();
        bundle.putString("uri", "content://" + AUTHORITY + "/" + MUSHROOMS_PATH);
        loaderManager.initLoader(0, bundle,this);                        // регистрируем лоадер, калбэки для него переопределяем в активити
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
        uiSettings = mMap.getUiSettings();
        uiSettings.setZoomControlsEnabled(true);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},2 );
        }
        else           // запрашиваем разрешение на определение местоположения, если его нет
        {
            mMap.setMyLocationEnabled(true);
            uiSettings.setMyLocationButtonEnabled(true);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED && requestCode == 2) {
            Toast.makeText(getBaseContext(),"Для определения местоположения требуется разрешение", Toast.LENGTH_LONG).show();
        } else
        {                                                   // если разрешение не получено - выдаем сообщение, если получено - включаем его и кнопку поиска
            mMap.setMyLocationEnabled(true);
            UiSettings uiSettings = mMap.getUiSettings();
            uiSettings.setMyLocationButtonEnabled(true);
        }
    }

    @NonNull
    @Override
    public Loader<Cursor> onCreateLoader(int id, @Nullable Bundle args) {          // данные в args можно было передать в initLoader, вы передаем только uri пока
        Log.d("LOG", "onCreateLoader");
        assert args != null;
        Uri uri = Uri.parse(args.getString("uri"));
        String[] projection = args.getStringArray("projection");
        String selection = args.getString("selection");
        String[] selectionArgs  = args.getStringArray("selectionArgs");
        String sortOrder  = args.getString("sortOrder");
        return new MyCursorAdapter(this, uri,projection,selection,selectionArgs,sortOrder);
    }


    @Override
    public void onLoadFinished(@NonNull Loader<Cursor> loader, final Cursor data) {        // здесь имеем курсор -
        Log.d("LOG", "onLoadFinished");
        simpleCursorAdapter.swapCursor(data);    // заменяем курсор в адаптере на новый
        drawPointsFromCursor(data);              // рисуем по нему точки на карте
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {       // вешаем слушателей на клик по пунктам листа - перемещение карты на соответствующий указатель
                data.moveToPosition(position);
                LatLng latLng = new LatLng(data.getDouble(data.getColumnIndex(COORDINATE_X_COLUMN_NAME)), data.getDouble(data.getColumnIndex(COORDINATE_Y_COLUMN_NAME)));
                mMap.animateCamera(CameraUpdateFactory.newCameraPosition(new CameraPosition.Builder().target(latLng).zoom(16).build()));
            }
        });
    }

    @Override
    public void onLoaderReset(@NonNull Loader<Cursor> loader) {

    }

    void drawPointsFromCursor(Cursor cursor)
    {
        mMap.clear();
        MarkerOptions markerOptions = new MarkerOptions();

        if (cursor!=null && cursor.moveToFirst()) {
            LatLng latLng = new LatLng(cursor.getDouble(cursor.getColumnIndex(COORDINATE_X_COLUMN_NAME)), cursor.getDouble(cursor.getColumnIndex(COORDINATE_Y_COLUMN_NAME)));
            markerOptions.position(latLng);
            markerOptions.title(cursor.getString(cursor.getColumnIndex(DESCRIPTION_COLUMN_NAME)));
            mMap.addMarker(markerOptions);
            mMap.animateCamera(CameraUpdateFactory.newCameraPosition(new CameraPosition.Builder().target(latLng).zoom(10).build()));
            do {
                markerOptions.title(cursor.getString(cursor.getColumnIndex(DESCRIPTION_COLUMN_NAME)));
                markerOptions.position(new LatLng(cursor.getDouble(cursor.getColumnIndex(COORDINATE_X_COLUMN_NAME)), cursor.getDouble(cursor.getColumnIndex(COORDINATE_Y_COLUMN_NAME))));
                mMap.addMarker(markerOptions);
            }
            while (cursor.moveToNext());
        }
    }

  static class MyCursorAdapter extends CursorLoader
    {
        Uri uri;
        String[] projection;
        String selection;
        String[] selectionArgs;
        String sortOrder;
        Context context;

        public MyCursorAdapter(@NonNull Context context, @NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
            super(context, uri, projection, selection, selectionArgs, sortOrder);
            this.uri = uri;
            this.projection = projection;
            this.selection = selection;
            this.selectionArgs = selectionArgs;
            this.sortOrder = sortOrder;
            this.context = context;
        }

        @Override
        public Cursor loadInBackground() {
            return context.getContentResolver().query(uri,projection,selection,selectionArgs,sortOrder);   // получение курсора из content provider-а
        }

    }
}