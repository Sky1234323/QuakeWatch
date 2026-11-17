package com.akash.quakewatch.ui.insights;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.akash.quakewatch.R;
import com.akash.quakewatch.analytics.AnalyticsEngine;
import com.akash.quakewatch.data.model.Quake;
import com.akash.quakewatch.databinding.FragmentInsightsBinding;
import com.akash.quakewatch.util.MagnitudeUtils;
import com.akash.quakewatch.viewmodel.QuakeViewModel;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * InsightsFragment presents charts and statistical analysis of earthquake data,
 * including daily frequency bar charts, magnitude donut charts, activity trend lines,
 * and focal depth breakdown indicators.
 */
public class InsightsFragment extends Fragment {

    private FragmentInsightsBinding mBinding;
    private QuakeViewModel mViewModel;
    private int mSelectedWindowDays = 7;
    private List<Quake> mLatestQuakes = Collections.emptyList();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mBinding = FragmentInsightsBinding.inflate(inflater, container, false);
        return mBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mViewModel = new ViewModelProvider(requireActivity()).get(QuakeViewModel.class);

        setupControls();
        observeViewModel();
    }

    private void setupControls() {
        mBinding.toggleTimeWindow.check(R.id.btn_window_7d);
        mBinding.toggleTimeWindow.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;

            if (checkedId == R.id.btn_window_7d) {
                mSelectedWindowDays = 7;
            } else if (checkedId == R.id.btn_window_30d) {
                mSelectedWindowDays = 30;
            }
            refreshAnalysis();
        });
    }

    private void observeViewModel() {
        mViewModel.getAllQuakes().observe(getViewLifecycleOwner(), quakes -> {
            mLatestQuakes = quakes != null ? quakes : Collections.emptyList();
            refreshAnalysis();
        });
    }

    private void refreshAnalysis() {
        if (mBinding == null) return;

        AnalyticsEngine.AnalysisResult result = AnalyticsEngine.analyze(
                mLatestQuakes, mSelectedWindowDays, System.currentTimeMillis());

        renderSummaryAndStats(result);
        renderBarChart(result);
        renderPieChart(result);
        renderLineChart(result);
        renderDepthBreakdown(result);
    }

    private void renderSummaryAndStats(AnalyticsEngine.AnalysisResult res) {
        if (res.total == 0) {
            mBinding.cardEmptyState.setVisibility(View.VISIBLE);
            mBinding.cardSummary.setVisibility(View.GONE);
        } else {
            mBinding.cardEmptyState.setVisibility(View.GONE);
            mBinding.cardSummary.setVisibility(View.VISIBLE);
        }

        mBinding.tvSummarySentence.setText(res.summarySentence);
        mBinding.tvStatTotal.setText(String.valueOf(res.total));

        if (res.strongest != null) {
            mBinding.tvStatStrongestMag.setText(String.format(Locale.US, "M%.1f", res.strongest.getMagnitude()));
            mBinding.tvStatStrongestPlace.setText(res.strongest.getPlace());
            int colorRes = MagnitudeUtils.severity(res.strongest.getMagnitude()).getColorRes();
            mBinding.tvStatStrongestMag.setTextColor(ContextCompat.getColor(requireContext(), colorRes));
        } else {
            mBinding.tvStatStrongestMag.setText("--");
            mBinding.tvStatStrongestPlace.setText("None");
            mBinding.tvStatStrongestMag.setTextColor(ContextCompat.getColor(requireContext(), R.color.qw_text_primary));
        }

        mBinding.tvStatAvgMag.setText(res.total > 0 ? String.format(Locale.US, "M%.1f", res.avgMagnitude) : "M--");
        mBinding.tvStatAvgDepth.setText(res.total > 0 ? String.format(Locale.US, "%.0f km", res.avgDepthKm) : "-- km");
    }

    private void renderBarChart(AnalyticsEngine.AnalysisResult res) {
        BarChart barChart = mBinding.chartBarDaily;

        if (res.perDay == null || res.perDay.length == 0 || res.total == 0) {
            barChart.clear();
            barChart.setNoDataText("No earthquake data available");
            barChart.invalidate();
            return;
        }

        List<BarEntry> entries = new ArrayList<>();
        for (int i = 0; i < res.perDay.length; i++) {
            entries.add(new BarEntry(i, res.perDay[i]));
        }

        int primaryColor = ContextCompat.getColor(requireContext(), R.color.qw_primary);
        int textColor = ContextCompat.getColor(requireContext(), R.color.qw_text_secondary);
        int borderColor = ContextCompat.getColor(requireContext(), R.color.qw_border);

        BarDataSet set = new BarDataSet(entries, "Earthquakes per Day");
        set.setColor(primaryColor);
        set.setValueTextColor(textColor);
        set.setValueTextSize(9f);
        set.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return value > 0 ? String.valueOf((int) value) : "";
            }
        });

        BarData data = new BarData(set);
        data.setBarWidth(0.55f);

        barChart.setData(data);
        barChart.getDescription().setEnabled(false);
        barChart.getLegend().setEnabled(false);
        barChart.setFitBars(true);
        barChart.setScaleEnabled(false);
        barChart.setTouchEnabled(false);

        // X-axis configuration
        XAxis xAxis = barChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setTextColor(textColor);
        xAxis.setTextSize(9f);
        xAxis.setValueFormatter(new IndexAxisValueFormatter(res.dayLabels));
        xAxis.setGranularity(1f);
        xAxis.setLabelCount(Math.min(res.dayLabels.length, 7));

        // Y-axis configuration
        YAxis leftAxis = barChart.getAxisLeft();
        leftAxis.setTextColor(textColor);
        leftAxis.setTextSize(9f);
        leftAxis.setGridColor(borderColor);
        leftAxis.setAxisMinimum(0f);
        leftAxis.setGranularity(1f);

        barChart.getAxisRight().setEnabled(false);

        barChart.animateY(400);
        barChart.invalidate();
    }

    private void renderPieChart(AnalyticsEngine.AnalysisResult res) {
        PieChart pieChart = mBinding.chartPieMagnitude;

        if (res.total == 0) {
            pieChart.clear();
            pieChart.setNoDataText("No earthquake data available");
            pieChart.invalidate();
            return;
        }

        List<PieEntry> entries = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();

        String[] bandLabels = new String[]{"< 3.0", "3.0\u20133.9", "4.0\u20134.9", "5.0\u20135.9", "6.0+"};
        int[] bandColors = new int[]{
                ContextCompat.getColor(requireContext(), R.color.qw_band_micro),
                ContextCompat.getColor(requireContext(), R.color.qw_sev_low),
                ContextCompat.getColor(requireContext(), R.color.qw_sev_moderate),
                ContextCompat.getColor(requireContext(), R.color.qw_sev_strong),
                ContextCompat.getColor(requireContext(), R.color.qw_sev_major)
        };

        for (int i = 0; i < res.magnitudeBands.length; i++) {
            if (res.magnitudeBands[i] > 0) {
                entries.add(new PieEntry(res.magnitudeBands[i], bandLabels[i]));
                colors.add(bandColors[i]);
            }
        }

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(colors);
        dataSet.setSliceSpace(2f);
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setValueTextSize(11f);
        dataSet.setValueTypeface(Typeface.DEFAULT_BOLD);
        dataSet.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.valueOf((int) value);
            }
        });

        PieData data = new PieData(dataSet);
        pieChart.setData(data);

        // Donut styling
        pieChart.getDescription().setEnabled(false);
        pieChart.setDrawHoleEnabled(true);
        pieChart.setHoleColor(ContextCompat.getColor(requireContext(), R.color.qw_surface));
        pieChart.setHoleRadius(56f);
        pieChart.setTransparentCircleRadius(0f);
        pieChart.setCenterText("M\u22652.5\n" + res.total + " events");
        pieChart.setCenterTextSize(13f);
        pieChart.setCenterTextColor(ContextCompat.getColor(requireContext(), R.color.qw_text_primary));
        pieChart.setCenterTextTypeface(Typeface.SERIF);
        pieChart.setDrawEntryLabels(false);
        pieChart.setTouchEnabled(false);

        // Legend configuration
        Legend legend = pieChart.getLegend();
        legend.setEnabled(true);
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.BOTTOM);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
        legend.setOrientation(Legend.LegendOrientation.HORIZONTAL);
        legend.setDrawInside(false);
        legend.setTextColor(ContextCompat.getColor(requireContext(), R.color.qw_text_secondary));
        legend.setTextSize(10f);
        legend.setForm(Legend.LegendForm.CIRCLE);
        legend.setWordWrapEnabled(true);

        pieChart.animateY(400);
        pieChart.invalidate();
    }

    private void renderLineChart(AnalyticsEngine.AnalysisResult res) {
        LineChart lineChart = mBinding.chartLineTrend;

        if (res.maxPerDay == null || res.maxPerDay.length == 0 || res.total == 0) {
            lineChart.clear();
            lineChart.setNoDataText("No earthquake data available");
            lineChart.invalidate();
            return;
        }

        List<Entry> peakEntries = new ArrayList<>();
        List<Entry> movingAvgEntries = new ArrayList<>();

        for (int i = 0; i < res.maxPerDay.length; i++) {
            peakEntries.add(new Entry(i, (float) res.maxPerDay[i]));
            movingAvgEntries.add(new Entry(i, (float) res.movingAvg3[i]));
        }

        int primaryColor = ContextCompat.getColor(requireContext(), R.color.qw_primary);
        int movingAvgColor = ContextCompat.getColor(requireContext(), R.color.qw_sev_strong);
        int textColor = ContextCompat.getColor(requireContext(), R.color.qw_text_secondary);
        int borderColor = ContextCompat.getColor(requireContext(), R.color.qw_border);

        // Dataset 1: Daily Max Magnitude (Left axis)
        LineDataSet peakSet = new LineDataSet(peakEntries, "Peak Mag");
        peakSet.setColor(primaryColor);
        peakSet.setCircleColor(primaryColor);
        peakSet.setLineWidth(2f);
        peakSet.setCircleRadius(3.5f);
        peakSet.setDrawCircleHole(false);
        peakSet.setValueTextColor(primaryColor);
        peakSet.setValueTextSize(8.5f);
        peakSet.setAxisDependency(YAxis.AxisDependency.LEFT);
        peakSet.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return value > 0 ? String.format(Locale.US, "%.1f", value) : "";
            }
        });

        // Dataset 2: 3-day Moving Average count (Right axis)
        LineDataSet movingAvgSet = new LineDataSet(movingAvgEntries, "3d Avg Count");
        movingAvgSet.setColor(movingAvgColor);
        movingAvgSet.setCircleColor(movingAvgColor);
        movingAvgSet.setLineWidth(1.8f);
        movingAvgSet.setCircleRadius(2.5f);
        movingAvgSet.setDrawCircleHole(false);
        movingAvgSet.setValueTextColor(movingAvgColor);
        movingAvgSet.setValueTextSize(8.5f);
        movingAvgSet.setAxisDependency(YAxis.AxisDependency.RIGHT);
        movingAvgSet.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return value > 0 ? String.format(Locale.US, "%.1f", value) : "";
            }
        });

        LineData lineData = new LineData(peakSet, movingAvgSet);
        lineChart.setData(lineData);

        lineChart.getDescription().setEnabled(false);
        lineChart.setScaleEnabled(false);
        lineChart.setTouchEnabled(false);

        // X-axis
        XAxis xAxis = lineChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setTextColor(textColor);
        xAxis.setTextSize(9f);
        xAxis.setValueFormatter(new IndexAxisValueFormatter(res.dayLabels));
        xAxis.setGranularity(1f);
        xAxis.setLabelCount(Math.min(res.dayLabels.length, 7));

        // Left axis: Magnitude
        YAxis leftAxis = lineChart.getAxisLeft();
        leftAxis.setTextColor(primaryColor);
        leftAxis.setTextSize(9f);
        leftAxis.setGridColor(borderColor);
        leftAxis.setAxisMinimum(0f);
        leftAxis.setAxisMaximum(8f);

        // Right axis: Count
        YAxis rightAxis = lineChart.getAxisRight();
        rightAxis.setEnabled(true);
        rightAxis.setTextColor(movingAvgColor);
        rightAxis.setTextSize(9f);
        rightAxis.setDrawGridLines(false);
        rightAxis.setAxisMinimum(0f);

        // Legend
        Legend legend = lineChart.getLegend();
        legend.setEnabled(true);
        legend.setTextColor(textColor);
        legend.setTextSize(10f);
        legend.setForm(Legend.LegendForm.LINE);

        lineChart.animateX(400);
        lineChart.invalidate();
    }

    private void renderDepthBreakdown(AnalyticsEngine.AnalysisResult res) {
        int total = res.total;

        int shallowPct = total > 0 ? (int) Math.round(((double) res.shallowCount / total) * 100.0) : 0;
        int intermediatePct = total > 0 ? (int) Math.round(((double) res.intermediateCount / total) * 100.0) : 0;
        int deepPct = total > 0 ? (int) Math.round(((double) res.deepCount / total) * 100.0) : 0;

        mBinding.progressShallow.setProgress(shallowPct);
        mBinding.tvDepthShallowCount.setText(String.format(Locale.US, "%d (%d%%)", res.shallowCount, shallowPct));

        mBinding.progressIntermediate.setProgress(intermediatePct);
        mBinding.tvDepthIntermediateCount.setText(String.format(Locale.US, "%d (%d%%)", res.intermediateCount, intermediatePct));

        mBinding.progressDeep.setProgress(deepPct);
        mBinding.tvDepthDeepCount.setText(String.format(Locale.US, "%d (%d%%)", res.deepCount, deepPct));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mBinding = null;
    }
}
