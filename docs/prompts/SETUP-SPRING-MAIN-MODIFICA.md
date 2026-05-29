Esegui le seguenti due modifiche:

1. In src/main/java/it/gavia/cloud/sostitutoincloud/config/SecurityConfig.java
   aggiungi dopo .cors(Customizer.withDefaults()):
   .httpBasic(Customizer.withDefaults())

2. Rinomina il package da it.gavia.cloud.sostitutoincloud se ancora non fatto
   a it.gavia.sostitutoincloud in TUTTI i file Java esistenti
   e sposta fisicamente le cartelle:
    - da: src/main/java/it/gavia/cloud/sostitutoincloud/
    - a:  src/main/java/it/gavia/sostitutoincloud/

   Aggiorna anche il pom.xml se contiene riferimenti
   al package completo.

Dopo le modifiche lancia:
mvn -Plocal clean package
e riporta l'output.