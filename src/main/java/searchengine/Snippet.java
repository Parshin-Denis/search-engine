package searchengine;

import lombok.Data;

import java.util.Map;
import java.util.TreeMap;

@Data
public class Snippet {
    private int beginIndex;
    private int endIndex;
    private String text;
    private TreeMap<Integer, String> queryWordsIndexes;

    public Snippet() {
        queryWordsIndexes = new TreeMap<>();
    }

    public String getFormattedText(int length){
        int formatBeginIndex = 0;
        int formatEndIndex = Math.min(text.length(), length);

        if (text.length() > length) {
            int firstQueryIndex = queryWordsIndexes.firstKey();
            int lastQueryIndex = queryWordsIndexes.lastKey() + queryWordsIndexes.lastEntry().getValue().length();

            if (lastQueryIndex - firstQueryIndex > length) {
                formatBeginIndex = firstQueryIndex;
                formatEndIndex = firstQueryIndex + Math.max(length, queryWordsIndexes.firstEntry().getValue().length());
            } else {
                int gap = (length - (lastQueryIndex - firstQueryIndex)) / 2;
                if (firstQueryIndex - gap > 0) {
                    formatBeginIndex = firstQueryIndex - gap;
                    formatEndIndex = lastQueryIndex + gap;
                }
                if (lastQueryIndex + gap > text.length()) {
                    formatEndIndex = text.length();
                    formatBeginIndex = text.length() - length;
                }
            }
        }
        return getCutBoldText(formatBeginIndex, formatEndIndex);
    }

    private String getCutBoldText(int formatBeginIndex, int formatEndIndex) {
        String formattedText = text.substring(formatBeginIndex, formatEndIndex);
        Map.Entry<Integer, String> entry = queryWordsIndexes.lastEntry();
        while (entry != null){
            if (entry.getKey() + entry.getValue().length() <= formatEndIndex) {
                formattedText = formattedText.substring(0, entry.getKey() - formatBeginIndex)
                        .concat("<b>")
                        .concat(entry.getValue())
                        .concat("</b>")
                        .concat(formattedText.substring(entry.getKey() + entry.getValue().length()
                                - formatBeginIndex));
            }
            entry = queryWordsIndexes.lowerEntry(entry.getKey());
        }
        return formattedText;
    }
}
