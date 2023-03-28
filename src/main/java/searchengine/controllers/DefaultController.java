package searchengine.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import searchengine.repositories.SiteRepository;

@Controller
public class DefaultController {
    @Autowired
    private SiteRepository siteRepository;
    /**
     * Метод формирует страницу из HTML-файла index.html,
     * который находится в папке resources/templates.
     * Это делает библиотека Thymeleaf.
     */
    @RequestMapping("/")
    public String index(Model model) {
//        model.addAttribute("allSites", siteRepository.findAll().get(0).getName());
        return "index";
    }
}
