package com.playcode.runrunrun.fragment;


import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.orangegangsters.github.swipyrefreshlayout.library.SwipyRefreshLayout;
import com.orangegangsters.github.swipyrefreshlayout.library.SwipyRefreshLayoutDirection;
import com.playcode.runrunrun.App;
import com.playcode.runrunrun.R;
import com.playcode.runrunrun.activity.ShowDetailActivity;
import com.playcode.runrunrun.adapter.RunCircleAdapter;
import com.playcode.runrunrun.model.RecordsEntity;
import com.playcode.runrunrun.model.RunCircleResultModel;
import com.playcode.runrunrun.utils.APIUtils;
import com.playcode.runrunrun.utils.RetrofitHelper;
import com.playcode.runrunrun.utils.RxBus;

import java.util.ArrayList;
import java.util.List;

import rx.Observer;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import rx.subscriptions.CompositeSubscription;

/**
 * A simple {@link Fragment} subclass.
 */
public class RunCircleFragment extends Fragment implements SwipyRefreshLayout.OnRefreshListener {
    private SwipyRefreshLayout mSwipeRefreshLayout;
    private RunCircleAdapter mRunCircleAdapter;
    private CompositeSubscription mSubscriptions;

    private List<RecordsEntity> list;
    private int lastItemId = 0;

    public RunCircleFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.fragment_run_circle, container, false);

        initView(rootView);

        return rootView;
    }

    private void initView(View rootView) {
        mSwipeRefreshLayout = (SwipyRefreshLayout) rootView.findViewById(R.id.srl_runCircle);
        RecyclerView recyclerView = (RecyclerView) rootView.findViewById(R.id.rv_runCircle);

        mSwipeRefreshLayout.setOnRefreshListener(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        mRunCircleAdapter = new RunCircleAdapter(new ArrayList<>(), getContext());

        mSubscriptions = new CompositeSubscription();

        recyclerView.setAdapter(mRunCircleAdapter);
    }

    @Override
    public void onStart() {
        super.onStart();
        mSubscriptions.add(RxBus.getInstance()
                .toObserable(RecordsEntity.class)
                .subscribe(recordsEntity -> {
                    Intent intent1 = new Intent(getActivity(), ShowDetailActivity.class);
                    intent1.putExtra("runRecord", recordsEntity);
                    startActivity(intent1);
                }));
    }

    @Override
    public void onResume() {
        super.onResume();
        initRunCircle();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mSubscriptions.unsubscribe();
    }

    private void initRunCircle() {
        if (App.getServerMode() == App.SERVER_MODE.WITHOUT_SERVER)
            return;
        SharedPreferences preferences = getActivity().getSharedPreferences("UserData", 0);
        String token = preferences.getString("token", "");
        if (TextUtils.isEmpty(token)) {
            Toast.makeText(getContext(), R.string.login_please, Toast.LENGTH_SHORT).show();
            return;
        }
        refreshData(token);
    }

    @Override
    public void onRefresh(SwipyRefreshLayoutDirection direction) {
        if (direction == SwipyRefreshLayoutDirection.TOP) {//下拉刷新
            initRunCircle();
        } else if (direction == SwipyRefreshLayoutDirection.BOTTOM) {//上拉加载更多
            SharedPreferences preferences = getActivity().getSharedPreferences("UserData", 0);
            String token = preferences.getString("token", "");
            if (TextUtils.isEmpty(token)) {
                Toast.makeText(getContext(), R.string.login_please, Toast.LENGTH_SHORT).show();
                return;
            }
            loadMore(token);
        }
    }

    private void refreshData(String token) {

        RetrofitHelper.getInstance()
                .getService(APIUtils.class)
                .getMaxId()
                .subscribeOn(Schedulers.io())
                .flatMap(maxIdModel -> RetrofitHelper.getInstance()
                        .getService(APIUtils.class)
                        .getRecordsById(maxIdModel.getId(), token))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<RunCircleResultModel>() {
                    @Override
                    public void onCompleted() {
                        mSwipeRefreshLayout.setRefreshing(false);
                    }

                    @Override
                    public void onError(Throwable e) {
                        Toast.makeText(getActivity(), R.string.refresh_failed_network,
                                Toast.LENGTH_SHORT).show();
                        mSwipeRefreshLayout.setRefreshing(false);
                    }

                    @Override
                    public void onNext(RunCircleResultModel runCircleResultModel) {
                        if (runCircleResultModel.getResultCode() == 0) {
                            list = runCircleResultModel.getRecords();
                            mRunCircleAdapter.resetData(list);
                            lastItemId = runCircleResultModel.getRecords()
                                    .get(runCircleResultModel.getRecords().size() - 1)
                                    .get_id();
                        } else {
                            Toast.makeText(getActivity(),
                                    getContext().getString(R.string.refresh_failed_reason) +
                                            runCircleResultModel.getMessage(), Toast.LENGTH_SHORT)
                                    .show();
                        }
                    }
                });
    }

    private void loadMore(String token) {
        RetrofitHelper.getInstance()
                .getService(APIUtils.class)
                .getRecordsById(lastItemId - 1, token)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<RunCircleResultModel>() {
                    @Override
                    public void onCompleted() {
                        mSwipeRefreshLayout.setRefreshing(false);
                    }

                    @Override
                    public void onError(Throwable e) {
                        e.printStackTrace();
                        Toast.makeText(getContext(), R.string.load_failed_network, Toast.LENGTH_SHORT).show();
                        mSwipeRefreshLayout.setRefreshing(false);
                    }

                    @Override
                    public void onNext(RunCircleResultModel runCircleResultModel) {
                        if (runCircleResultModel.getResultCode() == 0) {
                            if (runCircleResultModel.getRecords().size() > 0) {
                                mRunCircleAdapter.updateDataSet(runCircleResultModel.getRecords());
                                lastItemId = runCircleResultModel.getRecords()
                                        .get(runCircleResultModel.getRecords().size() - 1)
                                        .get_id();
                            } else {
                                Toast.makeText(getContext(), R.string.no_more, Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(getContext(), getContext().getString(R.string.load_failed_reason) +
                                    runCircleResultModel.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }
}
