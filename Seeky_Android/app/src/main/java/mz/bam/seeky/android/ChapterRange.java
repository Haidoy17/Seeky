package mz.bam.seeky.android;

/** Inclusive, validated chapter/page range. */
final class ChapterRange {
    final int first,last;
    ChapterRange(int first,int last,int total){if(total<1||first<0||last<first||last>=total)throw new IllegalArgumentException("Intervalo de capítulos inválido.");this.first=first;this.last=last;}
    int size(){return last-first+1;}
    static ChapterRange next(int current,int amount,int total){if(amount<1||amount>total-current)throw new IllegalArgumentException("Quantidade fora do livro.");return new ChapterRange(current,current+amount-1,total);}
}
