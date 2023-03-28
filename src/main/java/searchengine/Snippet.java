package searchengine;

import lombok.Data;

import java.util.Map;
import java.util.TreeMap;

@Data
public class Snippet {
    private String text;
    private TreeMap<Integer, String> queryWordsIndexes;

    public Snippet(String text) {
        this.text = text;
        queryWordsIndexes = new TreeMap<>();
    }

    public String getFormattedText(int length) {
        if (text.length() <= length) {
            return getCutBoldText(0, text.length());
        }
        int beginIndex = 0;
        int endIndex = length;
        int firstQueryIndex = queryWordsIndexes.firstKey();
        int lastQueryIndex = queryWordsIndexes.lastKey() + queryWordsIndexes.lastEntry().getValue().length();

        if (lastQueryIndex - firstQueryIndex > length) {
            beginIndex = firstQueryIndex;
            endIndex = firstQueryIndex + Math.max(length, queryWordsIndexes.firstEntry().getValue().length());
        } else {
            int gap = (length - (lastQueryIndex - firstQueryIndex)) / 2;
            if (firstQueryIndex - gap > 0) {
                beginIndex = firstQueryIndex - gap;
                endIndex = lastQueryIndex + gap;
            }
            if (lastQueryIndex + gap > text.length()) {
                endIndex = text.length();
                beginIndex = text.length() - length;
            }
        }

        return getCutBoldText(beginIndex, endIndex);
    }

    private String getCutBoldText(int beginIndex, int endIndex) {
        String formattedText = text.substring(beginIndex, endIndex);
        Map.Entry<Integer, String> entry = queryWordsIndexes.lastEntry();
        while (entry != null) {
            if (entry.getKey() + entry.getValue().length() <= endIndex) {
                formattedText = formattedText.substring(0, entry.getKey() - beginIndex)
                        .concat("<b>")
                        .concat(entry.getValue())
                        .concat("</b>")
                        .concat(formattedText.substring(entry.getKey() + entry.getValue().length()
                                - beginIndex));
            }
            entry = queryWordsIndexes.lowerEntry(entry.getKey());
        }
        return formattedText;
    }
}
