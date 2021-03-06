package com.dobe.zer0.coolweather.activity;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.dobe.zer0.coolweather.R;
import com.dobe.zer0.coolweather.db.CoolWeatherDB;
import com.dobe.zer0.coolweather.entity.City;
import com.dobe.zer0.coolweather.entity.County;
import com.dobe.zer0.coolweather.entity.Province;
import com.dobe.zer0.coolweather.util.HttpCallbackListener;
import com.dobe.zer0.coolweather.util.HttpUtil;
import com.dobe.zer0.coolweather.util.TransDatasUtil;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnItemClick;
import butterknife.OnItemSelected;

public class ChooseAreaActivity extends BaseActivity {
    private static final int LEVEL_PROVINCE = 0;
    private static final int LEVEL_CITY = 1;
    private static final int LEVEL_COUNTY = 2;

    private static final String SERVER_ADDRESS = "http://www.weather.com.cn/data/list3/city";
    private static final String STUFFIX_STR = ".xml";

    private int currentLevel;

    //use ButterKnife
    @BindView(R.id.title_text)
    TextView titleView;
    @BindView(R.id.list_view)
    ListView listView;

    private ArrayAdapter<String> adapter;
    private List<String> dataList;

    private ProgressDialog progressDialog;

    private CoolWeatherDB coolWeatherDB;

    /*
    province list from db
     */
    private List<Province> provinceList;

    /*
    city list from db
     */
    private List<City> cityList;

    /*
    county list from db
     */
    private List<County> countyList;

    /*
    selected province
     */
    private Province selectedProvince;

    /*
    selected city
     */
    private City selectedCity;

    /*
    is from weather layout falg
     */
    private boolean isFromWeatherLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        isFromWeatherLayout = getIntent().getBooleanExtra("from_weather_layout", false);

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        if((preferences.getBoolean("city_selected", false)) && !isFromWeatherLayout){
            Intent intent = new Intent(this, WeatherLayoutActivity.class);
            startActivity(intent);

            finish();
            return;
        }

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_choose_area);

//        titleView = (TextView) findViewById(R.id.title_text);
//        listView = (ListView) findViewById(R.id.list_view);
        //use ButterKnife
        ButterKnife.bind(this);

        dataList = new ArrayList<String>();
        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, dataList);

        listView.setAdapter(adapter);

        coolWeatherDB = CoolWeatherDB.getInstance(this);

        //init listeners
//        initListeners();

        //show all provinces in listview
        queryAllProvinces();
    }

    @OnItemClick(R.id.list_view)
    public void clickItemInListView(AdapterView<?> parent, View view, int position, long i) {
        if (currentLevel == LEVEL_PROVINCE) {
                    /*
                    all provinces in listview
                    get selected province
                    show all cities in listview
                     */
            selectedProvince = provinceList.get(position);

            queryAllCities();
        } else if (currentLevel == LEVEL_CITY) {
                    /*
                    all cities in listview
                    get selected city
                    show all counties in listview
                     */
            selectedCity = cityList.get(position);

            queryAllCounties();
        }else if(currentLevel == LEVEL_COUNTY){
            /*
            trans to WeatherLayoutActivity
             */
            String countyCode = countyList.get(position).getCountyCode();

            Intent intent = new Intent(ChooseAreaActivity.this, WeatherLayoutActivity.class);
            intent.putExtra("conunty_code", countyCode);

            startActivity(intent);
            finish();
        }
    }

    //query all provinces from db and init listview, titleview
    private void queryAllProvinces() {
        provinceList = coolWeatherDB.loadProvinces();

        if ((provinceList != null) && (!provinceList.isEmpty())) {
            dataList.clear();

            for (Province targetProvince : provinceList) {
                String provinceName = targetProvince.getProvinceName();

                if (provinceName != null) {
                    dataList.add(provinceName);
                }
            }

            //update adapter
            adapter.notifyDataSetChanged();
            //listview init selected first item
            listView.setSelection(0);
            //init titleview
            titleView.setText("中国");
        } else {
            queryDatasFromServer(null, "province");
        }

        //when back_normal will use currentLevel
        currentLevel = LEVEL_PROVINCE;
    }

    //query all cities from db and init listview, titlevie
    private void queryAllCities() {
        cityList = coolWeatherDB.loadCities(selectedProvince.getProvinceId());

        if ((cityList != null) && (!cityList.isEmpty())) {
            dataList.clear();

            for (City targetCity : cityList) {
                String cityName = targetCity.getCityName();

                if (cityName != null) {
                    dataList.add(cityName);
                }
            }

            //update adapter
            adapter.notifyDataSetChanged();
            //listview init selected first item
            listView.setSelection(0);
            //init titleview
            titleView.setText(selectedProvince.getProvinceName());
        } else {
            queryDatasFromServer(selectedProvince.getProvinceCode(), "city");
        }

        //when back_normal will use currentLevel
        currentLevel = LEVEL_CITY;
    }

    //query all counties from db and init listview, titlevie
    private void queryAllCounties() {
        countyList = coolWeatherDB.loadCounties(selectedCity.getCityId());

        if ((countyList != null) && (!countyList.isEmpty())) {
            dataList.clear();

            for (County targetCounty : countyList) {
                String countyName = targetCounty.getCountyName();

                if (countyName != null) {
                    dataList.add(countyName);
                }
            }

            //update adapter
            adapter.notifyDataSetChanged();
            //listview init selected first item
            listView.setSelection(0);
            //init titleview
            titleView.setText(selectedCity.getCityName());

        } else {
            queryDatasFromServer(selectedCity.getCityCode(), "county");
        }

        //when back_normal will use currentLevel
        currentLevel = LEVEL_COUNTY;
    }

    //query datas from server
    private void queryDatasFromServer(final String code, final String type) {
        String address = "";
        String sendMethod = "GET";

        if (TextUtils.isEmpty(code)) {
            address = SERVER_ADDRESS + STUFFIX_STR;
        } else {
            address = SERVER_ADDRESS + code + STUFFIX_STR;
        }

        showProgressDialog();
        HttpUtil.sendRequest(address, sendMethod, new HttpCallbackListener() {
            @Override
            public void onFinish(String response) {
                //data save from server to db is failed?
                boolean result = false;

                switch (type) {
                    case "province":
                        result = TransDatasUtil.transProvinceResponse(coolWeatherDB, response);
                        break;

                    case "city":
                        result = TransDatasUtil.transCityResponse(coolWeatherDB, response, selectedProvince.getProvinceId());
                        break;

                    case "county":
                        result = TransDatasUtil.transCountyResponse(coolWeatherDB, response, selectedCity.getCityId());
                        break;

                    default:
                        break;
                }

                //save datas in db successfully, use query*() update listview
                if (result) {
                    //in mainthread update UI
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            closeProgressDialog();

                            if ("province".equals(type)) {
                                queryAllProvinces();
                            } else if ("city".equals(type)) {
                                queryAllCities();
                            } else if ("county".equals(type)) {
                                queryAllCounties();
                            }
                        }
                    });
                }
            }

            @Override
            public void onExcepton(Exception e) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        closeProgressDialog();

                        Toast.makeText(ChooseAreaActivity.this, "Load failed.", Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    //show progress dialog when query datas from server
    private void showProgressDialog() {
        if (progressDialog == null) {
            progressDialog = new ProgressDialog(this);
            progressDialog.setMessage("Loading...");
            progressDialog.setCancelable(true);
            progressDialog.setCanceledOnTouchOutside(true);
        }

        progressDialog.show();
    }

    //close progress dialog when query datas from server
    private void closeProgressDialog() {
        if (progressDialog != null) {
            progressDialog.dismiss();
        }
    }

    @Override
    public void onBackPressed() {
        if (currentLevel == LEVEL_COUNTY) {
            queryAllCities();
        } else if (currentLevel == LEVEL_CITY) {
            queryAllProvinces();
        } else {
            if(isFromWeatherLayout){
                Intent intent = new Intent(this, WeatherLayoutActivity.class);

                startActivity(intent);
            }

            finish();
        }
    }
}
