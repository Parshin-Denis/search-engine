package searchengine;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;

import java.io.IOException;
import java.util.*;

public class LemmaFinder {
    private static LuceneMorphology luceneMorph;

    static {
        try {
            luceneMorph = new RussianLuceneMorphology();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static HashMap<String, Integer> getLemmaMap(String text) {
        HashMap<String, Integer> wordsMap = new HashMap<>();
        String textCleaned = removeHTMLTags(text);
        String[] words = splitToRussianWords(textCleaned);

        for (String word : words) {
            String lowerWord = word.toLowerCase();

            if (!isNormalWord(lowerWord)) {
                continue;
            }

            String baseWord = getNormalForm(lowerWord);

            if (wordsMap.containsKey(baseWord)) {
                wordsMap.put(baseWord, wordsMap.get(baseWord) + 1);
            } else {
                wordsMap.put(baseWord, 1);
            }
        }
        return wordsMap;
    }

    public static List<Snippet> getSnippetList(String text, Set<String> lemmas) {
        String textCleaned = removeHTMLTags(text);
        String[] words = splitToRussianWords(textCleaned);

        List<Snippet> snippetList = new ArrayList<>();
        Snippet snippet = null;
        int wordIndex = 0;

        for (String word : words) {
            String lowerWord = word.toLowerCase();

            if (!isNormalWord(lowerWord)) {
                continue;
            }

            String baseWord = getNormalForm(lowerWord);

            if (lemmas.contains(baseWord)) {
                wordIndex = textCleaned.indexOf(word, wordIndex + 1);
                if (snippet == null || wordIndex > snippet.getEndIndex()) {
                    snippet = new Snippet();
                    snippet.setBeginIndex(textCleaned.substring(0, wordIndex).lastIndexOf(">") + 1);
                    snippet.setEndIndex(textCleaned.indexOf("<", wordIndex));
                    snippet.setText(textCleaned.substring(snippet.getBeginIndex(), snippet.getEndIndex()));
                    snippetList.add(snippet);
                }
                snippet.getQueryWordsIndexes().put(wordIndex - snippet.getBeginIndex(), word);
            }
        }
        return snippetList;
    }

    public static Set<String> getLemmaSet(String query) {
        Set<String> lemmaSet = new HashSet<>();
        String[] words = splitToRussianWords(query);

        for (String word : words) {
            String lowerWord = word.toLowerCase();

            if (!isNormalWord(lowerWord)) {
                continue;
            }

            lemmaSet.add(getNormalForm(lowerWord));
        }
        return lemmaSet;
    }

    private static String removeHTMLTags(String text) {
        String regexScriptBlock = "<script[\\s\\S]*?</script>";

        String regexAllTags = "(<[^<>]+>\\s*)+";

        return text.replaceAll(regexScriptBlock, "")
                .replaceAll(regexAllTags, "<\n>");
    }

    public static String[] splitToRussianWords(String text) {
        String regexNotRussianLetters = "[^а-яёА-ЯЁ]+";
        return text.split(regexNotRussianLetters);
    }

    public static boolean isNormalWord(String word) {
        if (word.isBlank()) {
            return false;
        }
        String[] particleNames = new String[]{"ПРЕДЛ", "СОЮЗ", "ЧАСТ", "МЕЖД"};
        List<String> wordBaseForms = luceneMorph.getMorphInfo(word);
        return wordBaseForms.stream().anyMatch(s -> {
            for (String particle : particleNames) {
                if (s.contains(particle)) {
                    return false;
                }
            }
            return true;
        });
    }

    public static String getNormalForm(String word) {
        List<String> wordBaseForms = luceneMorph.getNormalForms(word);
        return wordBaseForms.get(0);
    }
}
