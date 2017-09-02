package kategory

@higherkind
@deriving(Monad::class, Traverse::class, MonoidK::class)
data class SequenceKW<out A> constructor(val sequence: Sequence<A>) : SequenceKWKind<A>, Sequence<A> by sequence {

    fun <B> flatMap(f: (A) -> SequenceKWKind<B>): SequenceKW<B> = this.ev().sequence.flatMap { f(it).ev().sequence }.k()

    fun <B> map(f: (A) -> B): SequenceKW<B> = this.ev().sequence.map(f).k()

    fun <B> foldL(b: B, f: (B, A) -> B): B = this.ev().fold(b, f)

    fun <B> foldR(lb: Eval<B>, f: (A, Eval<B>) -> Eval<B>): Eval<B> {
        fun loop(fa_p: SequenceKW<A>): Eval<B> = when {
            fa_p.sequence.none() -> lb
            else -> f(fa_p.ev().sequence.first(), Eval.defer { loop(fa_p.sequence.drop(1).k()) })
        }
        return Eval.defer { loop(this.ev()) }
    }

    fun <G, B> traverse(f: (A) -> HK<G, B>, GA: Applicative<G>): HK<G, SequenceKW<B>> =
            foldR(Eval.always { GA.pure(emptySequence<B>().k()) }) { a, eval ->
                GA.map2Eval(f(a), eval) { (sequenceOf(it.a) + it.b).k() }
            }.value()

    fun <B, Z> map2(fb: SequenceKWKind<B>, f: (Tuple2<A, B>) -> Z): SequenceKW<Z> =
            this.ev().flatMap { a ->
                fb.ev().map { b ->
                    f(Tuple2(a, b))
                }
            }.ev()

    companion object {

        fun <A> pure(a: A): SequenceKW<A> = sequenceOf(a).k()

        fun <A> empty(): SequenceKW<A> = emptySequence<A>().k()

        private tailrec fun <A, B> go(
                buf: MutableList<B>,
                f: (A) -> HK<SequenceKWHK, Either<A, B>>,
                v: SequenceKW<Either<A, B>>) {
            if (!v.isEmpty()) {
                val head: Either<A, B> = v.first()
                when (head) {
                    is Either.Right<A, B> -> {
                        buf += head.b
                        go(buf, f, v.drop(1).k())
                    }
                    is Either.Left<A, B> -> go(buf, f, (f(head.a).ev() + v.drop(1)).k())
                }
            }
        }

        fun <A, B> tailRecM(a: A, f: (A) -> HK<SequenceKWHK, Either<A, B>>): SequenceKW<B> {
            val buf = mutableListOf<B>()
            go(buf, f, f(a).ev())
            return SequenceKW(buf.asSequence())
        }

        fun functor(): SequenceKWHKMonadInstance = SequenceKW.monad()

        fun applicative(): SequenceKWHKMonadInstance = SequenceKW.monad()

        fun <A> semigroup(): SequenceKWMonoid<A> = object : SequenceKWMonoid<A> {}

        fun semigroupK(): SequenceKWHKMonoidKInstance = SequenceKW.monoidK()

        fun <A> monoid(): SequenceKWMonoid<A> = object : SequenceKWMonoid<A> {}

        fun foldable(): SequenceKWHKTraverseInstance = SequenceKW.traverse()
    }
}

fun <A> SequenceKW<A>.combineK(y: SequenceKWKind<A>): SequenceKW<A> = (this.sequence + y.ev().sequence).k()

fun <A> Sequence<A>.k(): SequenceKW<A> = SequenceKW(this)
