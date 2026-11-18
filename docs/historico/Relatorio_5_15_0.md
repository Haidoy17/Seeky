# Seeky 5.15.0

## Aparência

A apresentação segue as referências fornecidas pelo utilizador: ondas nos cabeçalhos e nos fundos, degradês, cartões arredondados, superfícies claras, ícones de categorias coloridos e destaque da secção activa na navegação. As ondas e ilustrações de pastas são caminhos vectoriais nativos, sem fotografias de ecrã incorporadas, bibliotecas novas ou animações permanentes. As cores derivam do tema escolhido; o roxo é o acento predefinido da apresentação clara.

O explorador apresenta capacidade utilizada e total obtidas do armazenamento acessível no aparelho. A biblioteca dispõe de cartões com capa, título, autor, favorito e progresso. O modo Grelha passa a ser a escolha inicial quando o utilizador ainda não guardou uma disposição; preferências de disposição já guardadas são respeitadas.

## Tamanhos

Há controlos explícitos −/+ no Início, em Exibir ficheiros e nas preferências. O tamanho dos ícones tem três níveis e afecta as categorias, os glifos dos ficheiros e a apresentação das capas. O Flash apresenta uma engrenagem com ajustes independentes para os ícones dos resultados e para o botão flutuante. O tamanho guardado da bolha mantém áreas de toque de 52, 60 ou 72 dp para desenhos de 44, 52 ou 64 dp, respectivamente. Os limites são verificados antes de guardar; o botão no limite fica desactivado.

## Contas e dados

Foram removidos os botões e acessos Google/Facebook da aplicação. O antigo AccountActivity é agora um redireccionamento interno para ferramentas de cópia local, sem login. O manifesto deixa de exportar esse destino e não contém o filtro OAuth seekyestudos://auth/callback. O aplicativo desactiva a preferência e cancela o agendamento do antigo backup automático ao iniciar. O agendador antigo deixa de criar tarefas mesmo que reste uma preferência antiga activada, e o seu serviço deixa de estar registado no manifesto. Não apaga contas dos fornecedores, dados remotos, estudos, livros, credenciais cifradas antigas ou ficheiros do utilizador. O backup local continua disponível.

Não foi alterada a base de dados remota, RLS, políticas, bibliotecas Supabase ou configuração de provedores. A revisão das instruções da integração limitou-se a confirmar que a remoção local não exigia modificar esses recursos.

## Recursos

Os testes revelaram que a actividade principal não fechava o seu Catalog após a saída. Agora são colocadas barreiras de conclusão nos três executores que podem usar esse recurso; o catálogo só fecha depois da escrita, da procura e da pesquisa terminarem ou serem canceladas. A interface não espera por esses executores e o banco não é fechado durante um acesso em curso.

## Verificação

- Conjunto completo de testes Android/Robolectric, incluindo os testes novos de desenho, tamanhos e fecho do catálogo.
- Conjunto completo de testes do núcleo: importação de Bíblias, pesquisa, ficheiros, notas, WAV e recuperação.
- Ensaios de limites dos controlos, persistência, bolha redimensionada, imagens e grelha, ausência de login social e retorno interno para cópias locais.
- Capturas nativas do Início, explorador, biblioteca, menu Mais e ajustes do Flash. As capas, nomes, percentagens e valores de armazenamento das capturas são dados sintéticos de ensaio.
- APK assinado com o certificado da versão anterior, mesma identidade mz.bam.seeky.v51, versão 5.15.0 / 5003600, nome instalado Seeky e alinhamento de 16 KB verificados.

O ficheiro release.json e os registos anexos apresentam os números exactos dos testes e o hash do APK. Os registos incluem falhas simuladas pelos testes de recuperação de narração; o resultado global encontra-se no fim. A validação foi automática; não houve teste num ITEL físico. O módulo de vozes não foi alterado e a gravação continua em WAV.

Haidoy Carlos Mautempo
