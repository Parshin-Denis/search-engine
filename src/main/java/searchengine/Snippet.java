package searchengine;

import lombok.Data;

import java.util.TreeMap;

@Data
public class Snippet {
    private int beginIndex;
    private int endIndex;
    private String text;
    private TreeMap<Integer, String> queryWordsIndexes;

    public Snippet(int beginIndex) {
        this.beginIndex = beginIndex;
        queryWordsIndexes = new TreeMap<>();
    }

    public String getSnippetText(String text, int length){
        endIndex = text.indexOf("<", beginIndex);
        int firstQueryIndex = queryWordsIndexes.firstKey();
        int lastQueryIndex = queryWordsIndexes.lastKey() + queryWordsIndexes.lastEntry().getValue().length() - 1;

        if (endIndex - beginIndex <= length) {
            this.text = text.substring(beginIndex, endIndex);
        }
        else if (lastQueryIndex - firstQueryIndex > length) {
            this.text = text.substring(firstQueryIndex,
                    firstQueryIndex + Math.max(length, queryWordsIndexes.firstEntry().getValue().length()));
        }
        else {
            int beginGap = (length - (lastQueryIndex - firstQueryIndex)) / 2;
            int endGap = beginGap;
            if (firstQueryIndex - beginGap < beginIndex) {
                beginGap = firstQueryIndex - beginIndex;
                endGap += endGap - beginGap;
            }
            if (lastQueryIndex + endGap > endIndex) {
                endGap = endIndex - lastQueryIndex;
                beginGap += beginGap - endGap;
            }
            this.text = text.substring(firstQueryIndex - beginGap, lastQueryIndex + endGap);
        }

        return getBoldText();
    }

    public String getBoldText() {
        String boldText = text;
        for (String word: queryWordsIndexes.values()){
            boldText = boldText.replaceAll(word, "<b>" + word + "</b>");
        }
        return boldText;
    }
}
