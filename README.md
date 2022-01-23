# skj2022_projekt
Projekt z przedmiotu SKJ na uczelni PJATK, rok 2021/22 (III semestr), Stanisław Knapiński (s22666).

## Kompilacja
```
./compile_node.bat // Dla networkNode
./compile_client.bat //Dla networkClient
```

## Uruchamianie
```
cd bin
java NetworkNode -ident <identyfikator> -tcpport <numer portu> -gateway <adres>:<port> <lista zasobów>
java NetworkClient -ident <identyfikator> -gateway <adres>:<port> <lista zasobów>
```

## Dokumentacja

javadoc w folderze docs/
dokumentacja algorytmu/opis w pliku Dokumentacja.pdf