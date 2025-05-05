# 🛠️ RefactorLombok

Projeto Java focado na **sanitização de código boilerplate** (getters e setters), substituindo métodos padrões por anotações do [Lombok](https://projectlombok.org/).

## ✨ Objetivo

Refatorar automaticamente trechos de código Java que contenham métodos `get` e `set` triviais, convertendo-os em anotações `@Getter` e `@Setter` diretamente na propriedade da classe — sem alterar métodos com lógica personalizada.

### Antes:
private String nome;
private Integer idade;

public String getNome() {
    return nome;
}

public void setNome(String nome) {
    this.nome = nome;
}

public Integer getIdade() {
  return idade;
}

public void setIdade(Integer idade) {
  if (idade > 18) {
    System.out.println("Menor de idade!");
  }
  this.idade = idade;
}

### Depois:

@Getter
@Setter
private String nome;

@Getter
private Integer idade;

public void setIdade(Integer idade) {
  if (idade > 18) {
    System.out.println("Menor de idade!");
  }
  this.idade = idade;
}

## Tecnologias utilizadas
Java 17
