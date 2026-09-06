package com.vein.theme;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ThemeConstituentRepository extends JpaRepository<ThemeConstituent, Long> {

    List<ThemeConstituent> findByThemeId(Long themeId);

    List<ThemeConstituent> findByThemeIdIn(List<Long> themeIds);
}
