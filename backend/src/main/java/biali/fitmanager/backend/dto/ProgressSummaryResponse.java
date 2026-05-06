package biali.fitmanager.backend.dto;

import java.util.List;

public class ProgressSummaryResponse {
    private int daysSinceFirstTraining;
    private String dateRange;
    private List<ExerciseProgress> progressList;
    private List<Integer> chartData; // np. wartości do narysowania słupków

    // Wewnętrzna klasa reprezentująca wiersz na liście (np. Martwy ciąg: 60kg -> 100kg)
    public static class ExerciseProgress {
        private String exerciseName;
        private int startWeight;
        private int endWeight;

        public ExerciseProgress(String exerciseName, int startWeight, int endWeight) {
            this.exerciseName = exerciseName;
            this.startWeight = startWeight;
            this.endWeight = endWeight;
        }

        // Gettery i Settery
        public String getExerciseName() { return exerciseName; }
        public void setExerciseName(String exerciseName) { this.exerciseName = exerciseName; }
        public int getStartWeight() { return startWeight; }
        public void setStartWeight(int startWeight) { this.startWeight = startWeight; }
        public int getEndWeight() { return endWeight; }
        public void setEndWeight(int endWeight) { this.endWeight = endWeight; }
    }

    // Gettery i Settery dla głównej klasy
    public int getDaysSinceFirstTraining() { return daysSinceFirstTraining; }
    public void setDaysSinceFirstTraining(int daysSinceFirstTraining) { this.daysSinceFirstTraining = daysSinceFirstTraining; }
    
    public String getDateRange() { return dateRange; }
    public void setDateRange(String dateRange) { this.dateRange = dateRange; }
    
    public List<ExerciseProgress> getProgressList() { return progressList; }
    public void setProgressList(List<ExerciseProgress> progressList) { this.progressList = progressList; }
    
    public List<Integer> getChartData() { return chartData; }
    public void setChartData(List<Integer> chartData) { this.chartData = chartData; }
}