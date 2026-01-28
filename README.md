#  KJPA - Kernon Java Persistence Architecture

O **KJPA** é um framework de persistência de alta performance construído sobre o **Hibernate 7**, projetado para automatizar o gerenciamento de transações, oferecer injeção de dependência assíncrona e garantir resiliência através do pool de conexões **HikariCP**.

---

## 🔗 Integração com o Ecossistema Kernon

O **KJPA** é o motor de persistência oficial projetado para trabalhar em simbiose com o [Kernon DI Framework](https://github.com/DanielTM999/kernon). Ele depende diretamente do core do Kernon para:

1. **Injeção de Configurações**: Utiliza o container do Kernon para injetar instâncias de `DatabaseConfiguration` e `DependencyContainer`.
2. **Boot Coordenado**: O ciclo de vida assíncrono do KJPA é gerenciado pelo executor central do Kernon, garantindo que o DataSource esteja disponível antes da inicialização dos serviços de negócio.
3. **Gerenciamento de Contexto**: As configurações de conexão são resolvidas dinamicamente, permitindo que o Kernon gerencie o escopo das Beans que dependem da persistência.

---

## Principais Diferenciais

* **Boot Assíncrono:** Inicialização do `SessionFactory` em background, evitando o travamento do startup da aplicação enquanto o banco de dados conecta.
* **AOP Transactional:** Gerenciamento de transações transparente via anotação `@Transactional` com suporte a transações aninhadas e fallback resiliente.
* **High-Performance Pooling:** Integração nativa com **HikariCP**, reduzindo drasticamente a latência de abertura de conexões e gerenciando o ciclo de vida dos sockets JDBC.
* **Logs de Diagnóstico Estruturados:** Relatórios visuais em blocos para falhas comuns (Driver, Dialeto, Conexão), transformando stacktraces confusas em soluções acionáveis.
* **Graceful Shutdown:** Sistema de encerramento automático que libera recursos do Hibernate e do Hikari ao desligar a JVM, evitando conexões "zumbis" no banco de dados.

---

## Tecnologias Base

* **Java 21+** (Text Blocks, Records, Virtual Threads ready)
* **[Kernon DI Framework](https://github.com/DanielTM999/kernon)**: O motor de injeção de dependência e AOP que sustenta o framework.
* **Hibernate 7.2.0.Final**
* **HikariCP 6.0.0**
* **Lombok**
* **SLF4J + Logback**

---

## Como Instalar (Maven)

Adicione as dependências transitivas através do módulo core no seu `pom.xml`:

```xml
<dependency>
    <groupId>dtm.database</groupId>
    <artifactId>kjpa</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Repositórios Dinâmicos (Proxy Pattern)

O coração da produtividade do KJPA está na definição de interfaces de repositório, o framework utiliza proxies dinâmicos para implementar a lógica de acesso a dados sem a necessidade de classes concretas.

```java
@Repository
public interface EntityTesteRepository extends CrudRepository<Cliente, Long> {

    // Query automática baseada no nome do método (Query Derivation)
    Cliente findByName(String name);

    // Query automática baseada no nome do método (Query Derivation)
    Cliente findByNameOrSobrenome(String name, String sobrename);

    /**
     * Exemplo de Query customizada (JPQL).
     * O KJPA valida em tempo de compilação se os parâmetros nomeados na Query 
     * possuem correspondentes no método através da anotação
     */
    @Query("""
             SELECT c
             FROM Cliente c
             WHERE c.nome = :nome
             AND c.sobrename = :sobrename
             AND c.id = :id 
            """)
    Cliente findByNameAndSobrenomeAndId(
            String name,
            @QueryParam String sobrename,
            @QueryParam("id") long idCliente
    );
}
```

##  Validação em Tempo de Compilação (Annotation Processing)

Diferente de frameworks que descobrem erros apenas quando a aplicação sobe, o KJPA utiliza o `RepositoryMetadataProcessor`. Este processador analisa seus `@Repository` durante a compilação e impede que o código seja compilado se houver erros de mapeamento.


### O que o Processor valida para você:

* **Integridade da Entidade:** Garante que a classe passada para o `CrudRepository<Entidade, ID>` esteja devidamente anotada com `@Entity` e possua um campo `@Id`.
* **Compatibilidade de Tipos:** Verifica se o tipo do `ID` no repositório coincide exatamente com o tipo do campo anotado com `@Id` na entidade.
* **Query Derivation:** Valida se as propriedades usadas em métodos como `findByNomeAndEmail` realmente existem na classe de entidade.
* **Assinaturas de Métodos:** Checa se o número de parâmetros no método condiz com a quantidade de critérios na query gerada.
* **Segurança em `@Query`:** Analisa queries JPQL e Nativa para garantir que parâmetros nomeados (ex: `:nome`) possuam correspondentes anotados com `@QueryParam` no método.

### Benefícios:
1. **Erro minimizados em Runtime:** Se o código compilou, a estrutura do seu repositório está correta.
2. **Performance:** Como os metadados são gerados no `compile-time`, o boot assíncrono do framework é muito mais rápido, pois ele não precisa "adivinhar" a estrutura das tabelas via reflexão pesada.
3. **Feedback Imediato:** O desenvolvedor recebe o erro diretamente no console do Maven/Gradle ou na aba de problemas da IDE.

```text
[ERRO] dtm.teste.EntityTesteRepository.java: Declaracao invalida. 
       O tipo do identificador da entidade (Usuario.id : Long) 
       nao e compativel com o tipo de ID do repositorio (String).
```

## Exemplo de Uso

## 🔌 Ativação e Bootstrapping

O KJPA foi desenhado de forma modular. Para que o **Kernon** reconheça e inicialize a infraestrutura de persistência, é necessário utilizar a anotação `@EnablePersistence` na sua classe principal ou de configuração.

```java
@EnablePersistence
@Application(name = "MinhaApp")
public class Main {
    public static void main(String[] args) {
        ManagedApplication.run(Main.class, args);
    }
}
```

### O que acontece ao utilizar `@EnablePersistence`:

Através do mecanismo de `@Import` do **Kernon**, esta anotação dispara a carga automática dos quatro pilares fundamentais do framework:

* **`HibernateConfiguration`**: Inicializa o `SessionFactory` e o `HikariDataSource` de forma assíncrona, garantindo que o pool de conexões esteja pronto sem bloquear o boot principal.
* **`RepositoryCreatorConfiguration`**: Ativa o escaneamento de pacotes e a criação dos **Proxies Dinâmicos** para todas as interfaces anotadas com `@Repository`.
* **`TransactionalAspect`**: Registra o interceptor AOP responsável por abrir, commitar ou reverter transações automaticamente.
* **`DatabaseSessionSynchronizationContextConfig`**: Configura o gerenciamento de sessões por Thread, garantindo que diferentes repositórios compartilhem a mesma transação dentro do mesmo fluxo de execução.

### 1.2 Definindo uma Entidade
O KJPA mapeia automaticamente classes anotadas com `@Entity` durante o startup, utilizando o escaneamento de pacotes do container de DI.

```java
@Entity
@Table(name = "clientes")
public class Cliente {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String nome;
    private String sobrenome;
}
```
### 2. Gerenciamento Transacional
Basta anotar o seu método para que o KJPA gerencie o ciclo de vida da `DatabaseSession` através de interceptação AOP.

```java
public class ClienteService {

    @Transactional
    public void processarCadastro(String nome) {
        // A transação é aberta automaticamente aqui
        Cliente c = new Cliente(nome);
        repository.save(c);
        
        // Em caso de exceção, o rollback estruturado é disparado automaticamente
    }
}
```
## 🔍 Sistema de Logs e Diagnóstico
O **KJPA** foi projetado para facilitar o debug em tempo de desenvolvimento. Em caso de erro, os logs seguem um padrão estruturado e legível, facilitando a identificação imediata da causa raiz:

```text
[ ERRO DE CONFIGURAÇÃO HIBERNATE ]
Ocorreu um erro interno ao inicializar os serviços do Hibernate.
> Verifique se o Dialeto (org.hibernate.dialect.PostgreSQLDialect) é compatível.
> Detalhe técnico: Unable to determine Dialect without JDBC metadata.
```

## 🏗️ Arquitetura Interna
O framework utiliza um `TransactionalAspect` que atua como um coordenador central entre a thread de execução e o pool de conexões.



* **Interceptação**: O Aspecto detecta a anotação `@Transactional` via Pointcut em tempo de execução.
* **Sincronização**: Verifica se a Thread já possui uma sessão ativa no `DatabaseSessionSynchronizationContext` para garantir o reuso de conexões em chamadas aninhadas.
* **Execução**: Caso não exista uma sessão ativa, solicita uma conexão ao **HikariCP** e inicia a transação física no banco de dados.
* **Finalização**: Realiza o `commit()` em caso de sucesso ou `rollback()` automático em caso de falha, garantindo a limpeza do contexto da thread para evitar vazamentos de memória.

---

## Shutdown Gracioso
Para garantir a integridade total dos dados e evitar conexões "zumbis" no servidor de banco de dados, o KJPA registra um *Shutdown Hook* que encerra os serviços na ordem correta de dependência:



```text
03:45:10 [Database-Shutdown-Hook] INFO - 
[ ENCERRANDO PERSISTÊNCIA ]
Iniciando o fechamento gracioso dos recursos de banco de dados...
> Fechando Hibernate SessionFactory...
> Fechando Pool de Conexões Hikari (Kernon-Pool)...
✓ Infraestrutura de persistência encerrada com sucesso.
```
