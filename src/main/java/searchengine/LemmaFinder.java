package searchengine;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

        Matcher matcher = getMatcherRussianWord(textCleaned);

        while (matcher.find()) {
            String baseWord = getNormalForm(matcher.group());

            if (baseWord.isBlank()) {
                continue;
            }

            if (wordsMap.containsKey(baseWord)) {
                wordsMap.put(baseWord, wordsMap.get(baseWord) + 1);
            } else {
                wordsMap.put(baseWord, 1);
            }
        }
        return wordsMap;
    }

    public static List<Snippet> getSnippetList(String text, Set<String> lemmas) {
        List<Snippet> snippetList = new ArrayList<>();

        String textCleaned = removeHTMLTags(text);

        String[] fragments = textCleaned.split("<\n>");
        for(String fragment: fragments){
            Snippet snippet = new Snippet(fragment);

            Matcher matcher = getMatcherRussianWord(fragment);
            while (matcher.find()){
                String baseWord = getNormalForm(matcher.group());
                if (lemmas.contains(baseWord)) {
                    snippet.getQueryWordsIndexes().put(matcher.start(), matcher.group());
                }
            }

            if (!snippet.getQueryWordsIndexes().isEmpty()){
                snippetList.add(snippet);
            }
        }
        return snippetList;
    }

    public static Set<String> getLemmaSet(String query) {
        Set<String> lemmaSet = new HashSet<>();

        Matcher matcher = getMatcherRussianWord(query);

        while (matcher.find()){
            String baseWord = getNormalForm(matcher.group());

            if (!baseWord.isBlank()) {
                lemmaSet.add(baseWord);
            }
        }
        return lemmaSet;
    }

    private static String removeHTMLTags(String text) {
        String regexScriptBlock = "<script[\\s\\S]*?</script>";

        String regexAllTags = "(<[^<>]+>\\s*)+";

        return text.replaceAll(regexScriptBlock, "")
                .replaceAll(regexAllTags, "<\n>");
    }

    public static Matcher getMatcherRussianWord(String text) {
        String regexRussianWord = "[а-яёА-ЯЁ]+";
        Pattern pattern = Pattern.compile(regexRussianWord);
        return pattern.matcher(text);
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
        String wordLowerCase = word.toLowerCase();
        if (!isNormalWord(wordLowerCase)) {
            return "";
        }
        List<String> wordBaseForms = luceneMorph.getNormalForms(wordLowerCase);
        return wordBaseForms.get(0);
    }
}
