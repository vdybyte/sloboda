package ru.sloboda.colony;

import net.minecraft.util.RandomSource;

public final class Names {
    private static final String[] PEOPLE = {
            "Фрол", "Ерёма", "Матвей", "Прохор", "Савва", "Тихон", "Ефим", "Кузьма",
            "Лука", "Никита", "Демид", "Игнат", "Остап", "Гордей", "Мирон", "Елисей",
            "Аксинья", "Марфа", "Пелагея", "Агафья", "Устинья", "Василиса", "Дарья",
            "Ульяна", "Фёкла", "Евдокия", "Ярослава", "Злата", "Любава", "Олеся"
    };

    private static final String[] SETTLEMENTS = {
            "Берёзовка", "Сосновка", "Ольховка", "Заречье", "Подгорье", "Ключи",
            "Верхние Луга", "Красный Яр", "Липовка", "Медвежий Лог", "Тихая Заводь",
            "Белые Камни", "Кедровка", "Светлый Бор", "Рябиновка", "Студёный Ключ"
    };

    private Names() {}

    public static String person(RandomSource random) {
        return PEOPLE[random.nextInt(PEOPLE.length)];
    }

    public static String settlement(RandomSource random) {
        return SETTLEMENTS[random.nextInt(SETTLEMENTS.length)];
    }
}
