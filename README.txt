SZAFA - zwierzatka i kostiumy (/szafa, /szafka)
================================================
Wymagania: Paper 1.21.4+ (Java 21). Bez zewnetrznych pluginow.

KOMPILACJA
  mvn clean package   ->  target/Szafa.jar  -> wrzuc do plugins/
  (albo zaimportuj folder jako projekt Maven w IntelliJ i zbuduj)

RESOURCE PACK
  Szafa-resourcepack.zip (osobny plik) trzeba wystawic graczom:
  server.properties -> resource-pack=<link do zipa>, resource-pack-sha1=<sha1>, require-resource-pack=true
  Bez packa zwierzatka beda papierem, a kostiumy zwyklymi zbrojami iron.
  Kostiumy maja czesci 3D na glowie (rogi, uszy, grzebien) - tez z packa.

KOMENDY
  /szafa, /szafka                                  - otwiera GUI
  /szafaadmin give <gracz> <pet|kostium> <id> <dni|perm>
  /szafaadmin take <gracz> <pet|kostium> <id>
  /szafaadmin list <gracz>
  /szafaadmin reload
  Dziala z konsoli (np. nagrody ze skrzynek: szafaadmin give %player% pet endermanek 7)

ID zwierzatek: endermanek, kotek, killerek
ID kostiumow:  koziolek, spiderman, lodowy_straznik

STEROWANIE W GUI
  /szafa: LPM na slocie = lista, PPM na slocie = zdejmij
  Lista zwierzatek: klik = zaloz / zdejmij
  Lista kostiumow:  PPM = zaloz / zdejmij

Wszystko (nazwy, opisy, bonusy) edytujesz w plugins/Szafa/config.yml.

USTAWIENIA ZWIERZATEK (config.yml)
  pet-scale: 0.5        - wielkosc zwierzatka (0.5 = wielkosc glowy gracza)
  pet-nametag: true     - napis z nazwa zwierzatka nad nim
  pet-yaw-offset: 0     - jesli zwierzatko patrzy tylem, ustaw 180
  world-model           - <nazwa>_head = latajaca glowa 3D (domyslnie), <nazwa>_model = cale zwierzatko
